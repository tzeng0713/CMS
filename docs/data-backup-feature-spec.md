# 資料備份功能規格書

本文件說明資料備份（Data Backup）功能的資料模型、業務邏輯、API 與前端操作流程，作為後續維護與驗收的依據。對應程式碼位置：

- 後端：`backend/src/main/java/com/example/cms/controller/BackupController.java`、`.../service/BackupService.java`、`.../dto/BackupTriggerRequest.java`、`.../service/AuthService.java`（`canManageBackup` 權限旗標）
- 資料庫：`backend/src/main/resources/schema.sql`（`backup_logs`）
- 設定：`backend/src/main/resources/application.yml`（`cms.backup.*`）
- 前端：`frontend/src/app/app.component.ts`、`app.component.html`（`activeView() === 'data-backup'` 區塊）、`frontend/src/app/core/cms-api.service.ts`

---

## 1. 功能總覽

| 功能 | 說明 |
|---|---|
| 手動觸發備份 | 主管在畫面上按「立即備份」，同步觸發匯出＋上傳流程，完成後才回應 |
| 匯出 Excel | 將固定範圍的四張資料表匯出成同一份 `.xlsx`，每張表各為一個分頁 |
| 上傳 Google Drive | 透過服務帳戶（Service Account）認證，把匯出的檔案上傳到指定資料夾 |
| 備份歷史紀錄 | 每次備份（不論成功或失敗）都會寫入 `backup_logs`，畫面上依時間新到舊列出 |

此功能**沒有自動排程**，比照業績結算、國稅局通報等功能的既有模式，沒有 `@Scheduled`，全部由主管手動在畫面上按「立即備份」觸發（詳見第 9 節已知限制）。

---

## 2. 資料表

### 2.1 `backup_logs`（新增）

| 欄位 | 型別 | 說明 |
|---|---|---|
| `backup_log_id` | BIGINT PK | 沿用專案既有慣例，以 `MAX(id)+1` 產生（`CmsJdbcSupport.nextId()`），非資料庫自動遞增 |
| `triggered_by` | BIGINT NOT NULL | 觸發者的 `staff_id`，外鍵指向 `staff` |
| `file_name` | VARCHAR(255) NOT NULL | 匯出的檔名（含時間戳記） |
| `drive_file_id` | VARCHAR(255) | Google Drive 上的檔案 ID；失敗時為 `NULL` |
| `drive_url` | VARCHAR(500) | Google Drive 可開啟的分享連結（`webViewLink`）；失敗時為 `NULL` |
| `status` | VARCHAR(20) NOT NULL | `SUCCESS` 或 `FAILED` |
| `error_message` | VARCHAR(1000) | 失敗原因；成功時為 `NULL` |
| `created_at` | TIMESTAMP | 觸發時間（`Asia/Taipei`），由後端寫入，非資料庫預設值 |

查詢歷史紀錄時會 `LEFT JOIN staff` 帶出 `triggered_by_name` 供畫面顯示。

### 2.2 沒有新增的表／欄位

- **來源資料表（`contracts`、`rent_payments`、`performance_bonuses`、`refunds`）完全不受影響**：備份只做 `SELECT *` 讀取，不寫入、不新增欄位、不做任何標記（例如「已備份」旗標）。
- **不做增量／差異備份**：每次觸發都是四張表的全量匯出，沒有「只匯出上次備份之後新增的資料」的機制。

---

## 3. 備份範圍與 Excel 產生邏輯

### 3.1 資料表範圍

固定為以下四張表（寫死在 `BackupService.BACKUP_TABLES`，**無法由前端或 API 參數調整**）：

1. `contracts`（合約）
2. `rent_payments`（收款）
3. `performance_bonuses`（業績獎金）
4. `refunds`（退款）

### 3.2 Excel 結構

- 一份 `.xlsx`，每張來源表各自一個分頁，分頁名稱即資料表名稱（`contracts`／`rent_payments`／`performance_bonuses`／`refunds`）。
- 表頭列直接使用資料庫欄位名稱（例如 `contract_id`、`rent`），不做欄位中文化或篩選，等同 `SELECT * FROM <table>` 的原始欄位。
- 儲存格型別對應：`Number` → 數字、`Boolean` → 布林、`java.sql.Timestamp`／`java.sql.Date` → 日期時間、字串以外型別一律轉字串寫入；`NULL` 值寫入空白儲存格。
- 若某張表目前沒有任何資料列，仍會建立該分頁，但只有分頁本身、沒有表頭與資料列。
- 欄寬套用 `autoSizeColumn`；表頭套用粗體樣式，比照 `TaxBureauNoticeService` 既有寫法。

### 3.3 檔名規則

```
CMS備份_{yyyyMMdd}_{HHmmss}.xlsx
```

時間戳記採觸發當下的 `Asia/Taipei` 時間，例如 `CMS備份_20260919_143000.xlsx`。

---

## 4. Google Drive 上傳

### 4.1 認證方式

- 使用 Google 官方 Java SDK（`google-api-services-drive`、`google-auth-library-oauth2-http`），以**服務帳戶（Service Account）**認證，範圍（scope）為 `DriveScopes.DRIVE_FILE`。
- 服務帳戶金鑰的**完整 JSON 內容**（非檔案路徑）由環境變數 `GOOGLE_DRIVE_SERVICE_ACCOUNT_KEY_JSON` 提供，於 `application.yml` 以 `cms.backup.google-service-account-json` 讀入，未設定時為空字串。
- 金鑰內容不寫死在程式碼、不進版控。

### 4.2 目標資料夾

- 由環境變數 `GOOGLE_DRIVE_BACKUP_FOLDER_ID` 提供，於 `application.yml` 以 `cms.backup.drive-folder-id` 讀入。
- 上傳時以 `File.setParents(List.of(driveFolderId))` 指定父資料夾。

### 4.3 前置設定（人工步驟，程式無法自動完成）

服務帳戶本身沒有自己的雲端硬碟儲存空間，**必須先到 Google Drive 網頁上，將目標資料夾「共用」給服務帳戶的 email**（格式類似 `xxx@xxx.iam.gserviceaccount.com`），並給予「編輯者」權限，否則上傳一律失敗（會反映在 `errorMessage`／`backup_logs.error_message` 中）。

### 4.4 上傳結果

上傳成功後取回：

- `id`：Google Drive 上的檔案 ID → 對應回應欄位 `driveFileId`
- `webViewLink`：可在瀏覽器開啟的分享連結 → 對應回應欄位 `driveUrl`

---

## 5. 權限控管

- 後端**不信任前端傳來的權限旗標**，每次觸發都用請求 body 內的 `staffId` 重新查詢角色：

  ```sql
  SELECT rp.role_name FROM staff s
  JOIN role_permissions rp ON rp.role_permission_id = s.role_permission_id
  WHERE s.staff_id = ?
  ```

- 查不到員工或角色不是「主管」，一律拋出 `ResponseStatusException(HttpStatus.FORBIDDEN)`，回傳 HTTP 403。
- 寫法比照 `RefundService.reviewRefund()`、`PerformanceBonusService.requireManager()`，各服務各自維護一份同樣邏輯的私有方法，專案目前沒有抽共用元件。
- 前端對應新增 `AuthUser.canManageBackup` 旗標（登入時由後端依角色計算好回傳，`"主管".equals(roleName)`），畫面上用 `canManageBackup()` 決定是否顯示「立即備份」按鈕與備份歷史列表；非主管進入「資料備份」頁面只會看到「僅主管可執行資料備份」的提示。

---

## 6. 執行方式與失敗處理

- **同步執行**：`POST /api/backups/trigger` 會等匯出＋上傳都完成才回應，前端顯示 loading 狀態直到收到結果，未做非同步／背景佇列處理。
- 權限檢查（403）發生在流程最前面，直接以例外中斷、不寫入 `backup_logs`。
- 通過權限檢查後，匯出或上傳過程中若拋出任何例外（Excel 產生失敗、Drive 憑證未設定、Drive API 呼叫失敗等），會被統一攔截：
  - 該次備份寫入 `backup_logs`，狀態為 `FAILED`，`error_message` 存實際例外訊息（`e.getMessage()`，若為 `null` 則存例外類別名稱）。
  - API 回應仍為 HTTP 200，但 `success: false`、`errorMessage` 帶上述訊息，**不會把例外整包吞掉**，前端會把這段文字直接顯示給主管看。
- 成功時 `backup_logs` 狀態為 `SUCCESS`，回應 `success: true`，並附上 `driveFileId`／`driveUrl`。

---

## 7. 前端操作流程

- 導覽列「其他查詢」群組新增「資料備份」項目（`data-backup`），所有登入者都看得到入口，實際操作權限由頁面內容決定（比照國稅局通報、業績結算等頁面的既有模式）。
- 主管畫面：
  1. 「立即備份」按鈕，按下後呼叫 `POST /api/backups/trigger`，按鈕在請求進行中停用並顯示「備份中…」。
  2. 成功：顯示備份完成時間、檔名，並提供可點擊開啟的 Google Drive 連結。
  3. 失敗：顯示「備份失敗：{errorMessage}」，訊息取自後端回傳的 `errorMessage`，非固定字串。
  4. 備份歷史列表：觸發者、時間、檔名、狀態（成功／失敗）、Drive 連結，依時間新到舊排序（`ORDER BY created_at DESC`），進入頁面時自動載入。
- 非主管畫面：僅顯示「僅主管可執行資料備份」提示，不顯示觸發按鈕與歷史列表。

---

## 8. API 一覽

| Method | Path | 說明 |
|---|---|---|
| POST | `/api/backups/trigger` | Body：`{ staffId }`。同步執行匯出＋上傳，回傳 `{ success, fileName, driveFileId, driveUrl, createdAt, errorMessage }`。非主管回傳 403 |
| GET | `/api/backups/logs` | 回傳備份歷史紀錄陣列，依 `created_at` 新到舊排序，每筆含 `triggered_by_name` |

---

## 9. 已知限制 / 待辦事項

- **完全手動觸發，沒有排程**：系統沒有 cron／`@Scheduled` 機制，多久備份一次完全依賴主管自己記得上畫面按「立即備份」，系統不會主動提醒。
- **服務帳戶資料夾共用是人工步驟**：部署或更換服務帳戶金鑰後，若忘記把目標資料夾共用給新的服務帳戶 email，上傳會直接失敗，需人工到 Google Drive 網頁設定，程式無法自動偵測或代為完成。
- **備份範圍固定寫死在程式碼**：四張表的清單目前是常數，不能透過 API 或畫面動態調整要備份哪些表；未來若要新增／移除表，需要改程式碼。
- **同步處理，可能受限於 HTTP 逾時**：資料量變大（例如租約或收款紀錄累積到相當規模）時，匯出＋上傳時間拉長，可能超過瀏覽器或反向代理的逾時設定而導致前端顯示失敗，但實際上傳可能仍在背景完成或已完成，此時 `backup_logs` 是否寫入視例外拋出的時間點而定，畫面上不一定能即時反映真實狀態。若資料量持續成長，建議評估改為非同步（例如先回傳「已受理」再背景執行，前端輪詢 `backup_logs`）。
- **沒有全量匯出以外的模式**：不支援增量備份、不支援排除特定欄位或篩選時間區間，每次都是四張表的完整 `SELECT *`。
- **沒有防止重複觸發的鎖定機制**：前端按鈕在請求進行中會停用，但這只防得住「同一個瀏覽器分頁」，不同分頁或不同主管同時觸發仍可能同時執行多次備份，各自各自寫入一筆 `backup_logs`，沒有互斥或排隊機制。
- **沒有專屬單元測試**：驗證方式為手動以 H2 seed data 啟動後端、呼叫 `trigger`／`logs` API 確認回傳內容與 Excel／Google Drive 檔案正確；未納入自動化測試套件。
