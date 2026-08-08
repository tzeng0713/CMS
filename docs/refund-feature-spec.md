# 退款管理功能規格書

本文件說明退款管理（Refund Management）功能的資料模型、業務邏輯、API 與前端操作流程，作為後續維護與驗收的依據。對應程式碼位置：

- 後端：`backend/src/main/java/com/example/cms/controller/RefundController.java`、`.../service/RefundService.java`
- 資料庫：`backend/src/main/resources/schema.sql`、`.../config/SchemaMigrationRunner.java`
- 前端：`frontend/src/app/app.component.ts`、`app.component.html`（`activeView() === 'refunds'` 區塊）、`frontend/src/app/core/cms-api.service.ts`

---

## 1. 功能總覽

| 功能 | 說明 |
|---|---|
| 新增退款 | 秘書搜尋客戶、選擇租約，填寫退款原因與金額明細後建立退款單 |
| 查詢退款 | 可依公司名稱、統一編號、建立日期區間、狀態篩選 |
| 修改退款 | 僅「草稿」「待審核」狀態可修改金額相關欄位；「審核通過」狀態僅能辦理退款 |
| 取消退款 | 除「已退款」外，任何狀態皆可取消 |
| 退款審核 | 僅「主管」角色可執行，審核通過後才能辦理退款 |
| 匯入收費清單 | 依收費清單編號帶出扣款金額與押金基礎金額，供新增/修改時參考填入 |
| 超額扣款轉應收帳單 | 扣款總額大於應退金額時，系統自動在**既有的 `charge_lists`（收費清單）表**建立一筆未結清帳單，共用同一套收費清單管理畫面編輯與收款，不另建新表 |

---

## 2. 資料表

### 2.1 `refunds`

| 欄位 | 型別 | 說明 |
|---|---|---|
| `refund_id` | BIGINT PK | 退款編號 |
| `customer_id` | BIGINT FK→customers | 客戶編號 |
| `contract_id` | BIGINT FK→contracts | 租約編號 |
| `charge_list_id` | BIGINT FK→charge_lists | 關聯收費清單（選填） |
| `company_name` | VARCHAR(255) | 建立當下的公司名稱快取 |
| `refund_reason` | VARCHAR(500) | 退款原因（原欄位 `reason`，已於 migration 改名） |
| `adjustment_amount` | DECIMAL(12,2) | 手動調整金額，可正可負，預設 0 |
| `adjustment_note` | VARCHAR(1000) | 調整金額備註 |
| `deduction_total` | DECIMAL(12,2) | 扣款總金額 |
| `refund_amount` | DECIMAL(12,2) | 系統計算之退款總金額（下限 0，見第 4.1 節） |
| `refund_status` | VARCHAR(20) | `草稿` / `待審核` / `審核通過` / `已退款` / `已取消` |
| `payment_method` / `bank_code` / `bank_account` / `bank_account_name` | | 退款方式與收款帳戶資訊 |
| `refunded_at` | VARCHAR(30) | 實際退款日期 |
| `termination_staff_id` | BIGINT FK→staff | 解約經辦人 |
| `created_by` / `created_at` | | 建立人／時間 |
| `reviewed_by` / `reviewed_at` | | 審核人／時間 |
| `updated_by` / `updated_at` | | 最後修改人／時間（即「最後修改」欄位，未另外新增 `last_modified_*`） |

> 原始應退金額（押金）**不落地存成欄位**，計算當下即時從 `contracts.deposit` 讀取（見 4.1）。

### 2.2 應收帳款：共用 `charge_lists`（不新增資料表）

超額扣款產生的「應收帳款」**不使用獨立資料表**，直接沿用既有的 `charge_lists`（收費清單）表建立一筆新紀錄：

| `charge_lists` 欄位 | 寫入值 | 說明 |
|---|---|---|
| `customer_id` / `contract_id` | 退款單的客戶／租約 | 與一般收費清單相同 |
| `fee_month` | `NULL` | 一般月費帳單一定有值（YYYY-MM），此欄位留空即可視為「非月費、退款差額」的判斷依據 |
| `advance_payment` | 差額絕對值 | 借用「代墊費」欄位承載差額金額（語意最接近：先幫客戶墊付、之後要跟客戶收回） |
| `total_amount` | 同 `advance_payment` | 比照一般收費清單「總額 = 各項金額加總」的計算方式 |
| `status` | `2`（未結清） | 沿用收費清單既有的已結清(1)/未結清(2)狀態機制 |
| `created_by` / `issued_at` / `updated_by` / `updated_at` | 建立時的操作人／時間 | |

- `refunds.charge_list_id` 會指回這筆新建立的收費清單，作為「這張收費清單是哪筆退款產生的」的唯一連結依據，不另外加欄位標記。
- 這筆收費清單會直接出現在**既有的「收費清單管理」列表與編輯畫面**（`ChargeListController`/`ChargeListService`），秘書可用原本就有的編輯功能調整金額或標記已結清，**不需要新的 UI**。
- 若退款當下 `charge_list_id` 已經有值（例如透過「匯入收費清單」帶入），系統不會另外新建一筆，直接沿用該筆既有收費清單作為應收帳款依據。

---

## 3. 退款狀態機

```
草稿 → 待審核 → 審核通過 → 已退款
  └──────┴──────────┴────────→ 已取消（除「已退款」外皆可取消）
```

- 「草稿」「待審核」：可自由修改所有金額與內容欄位。
- 「審核通過」：金額欄位鎖定，僅能填寫付款方式/收款帳戶/退款日期並將狀態轉為「已退款」（即「辦理退款」動作）。
- 「已退款」「已取消」：不可再修改。
- 退款完成（狀態轉為「已退款」）時，若該退款有關聯的 `charge_list_id`，系統會**回寫該筆收費清單 `status = 1`（已結清）**；完整的回壓時機（含哪些情境需要人工介入）見第 4.4 節。

---

## 4. 業務邏輯

### 4.1 金額計算（新增／修改時皆會重新計算）

```
refund_amount = contracts.deposit（押金） + adjustment_amount − deduction_total
```

- 若計算結果為負數：`refund_amount` 存為 `0`（不允許負數退款），並依第 2.2 節規則在 `charge_lists` 建立（或沿用既有）一筆未結清帳單。
  - API 回應會附帶 `message` 提示文字（例如：「扣款總額大於應退金額，差額 NT$X 已自動建立收費清單（未結清，編號 #Y），請至收費清單管理確認金額並收款。」），前端直接顯示於畫面上方提示列。

### 4.2 審核流程（對應需求 Q17）

無論客戶是否已向國稅局登記遷出，流程一致：

1. 秘書計算退款金額、建立退款單（狀態為「草稿」或直接送出「待審核」）。若建立時選了「草稿」，之後需在退款詳細資料彈窗按「送交審核」（`PUT /api/refunds/{id}`，`refundStatus` 改為「待審核」）才能進入下一步，否則永遠停在草稿。
2. 主管於退款詳細資料彈窗按下「審核通過」（`PATCH /api/refunds/{id}/review`）。
   - 後端會查詢該員工的角色是否為「主管」，非主管回傳 `403 Forbidden`。
   - 僅「待審核」狀態可被審核，否則回傳 `400`。
   - 審核通過後寫入 `reviewed_by`、`reviewed_at`，狀態轉為「審核通過」。
3. 主管尚未審核通過前，系統不提供「辦理退款」按鈕，無法跳過審核直接完成退款。
4. 秘書於「審核通過」狀態按下「辦理退款」，填入退款日期與付款資訊後送出，狀態轉為「已退款」。

### 4.3 超額扣款處理（對應需求 Q18）

- 判斷時機：新增與修改退款時都會重新計算並判斷（`RefundService.createRefund` / `updateRefund`）。
- 觸發條件：`deduction_total > 押金 + adjustment_amount`。
- 處理方式：
  1. 退款金額改存 `0`，**不會**讓退款單直接卡住無法儲存。
  2. 若退款當下**沒有**帶入 `chargeListId`：呼叫 `RefundService.createShortfallChargeList()` 在 `charge_lists` 建立一筆差額帳單（`status = 2` 未結清），並把新建立的 `charge_list_id` 寫回這筆退款。
  3. 若退款當下**已經**帶入 `chargeListId`（例如透過「匯入收費清單」）：不另外新建，視為沿用該筆既有收費清單即為應收帳款依據。
  4. 秘書可直接到「收費清單管理」畫面，用既有的編輯功能調整金額、確認收款後手動標記「已結清」。
- 退款狀態轉為「已退款」時，`RefundService.markChargeListSettled()` 會自動把關聯收費清單的 `status` 寫回 `1`（已結清），對應第 3 節的狀態機規則。

### 4.4 收費清單狀態回壓時機一覽

「回壓」指的是把 `refunds` 的狀態變化，反映到它所關聯的 `charge_lists.status` 上。目前**只有 1 個時間點是系統自動處理**，另外 **2 種情境系統完全不處理**，需要秘書自己到「收費清單管理」手動調整：

#### ✅ 自動回壓（系統做，不需要人工另外操作）

| 時間點 | 觸發方式 | 動作 |
|---|---|---|
| 退款「審核通過」→「已退款」 | 使用者按「辦理退款」送出表單（`PUT /api/refunds/{id}`） | `updateRefund()` 在同一次請求裡呼叫 `markChargeListSettled()`，把關聯 `charge_lists.status` 寫成 `1`（已結清） |

#### ⚠️ 需要人工介入（系統不會自動處理，屬於已知限制）

| 情境 | 為什麼系統不處理 | 秘書該做什麼 |
|---|---|---|
| **退款被取消**（任何狀態按「取消」） | `cancelRefund()` 只更新 `refunds.refund_status`，完全不觸碰已連結的 `charge_lists` | 若這筆退款先前因超額扣款建立過應收帳單，取消後該收費清單仍是「未結清」，需自行到收費清單管理判斷要作廢還是保留（`charge_lists` 只有已結清/未結清兩種狀態，沒有「已取消」可套用） |
| **退款編輯後不再超額扣款** | `updateRefund()` 只有在「本次計算仍然超額且尚未有 `chargeListId`」時才會建立新帳單；若退款原本已因超額建立過一筆收費清單，之後把調整金額/扣款總額改小、不再超額，**舊的收費清單不會被清除或改金額**，`refunds.charge_list_id` 也不會被清空 | 需自行發現這筆金額已經不準確的「未結清」收費清單，並手動修正或刪除 |

---

## 5. API 一覽

| Method | Path | 說明 | 權限 |
|---|---|---|---|
| GET | `/api/refunds` | 查詢列表，支援 `companyName`/`taxId`/`dateFrom`/`dateTo`/`status`/`page`/`pageSize`/`sortBy`/`sortDir` | 無限制 |
| GET | `/api/refunds/{id}` | 查詢單筆 | 無限制 |
| POST | `/api/refunds` | 新增退款 | 無限制 |
| PUT | `/api/refunds/{id}` | 修改退款（依狀態限制可改欄位，見第 3 節） | 無限制 |
| PATCH | `/api/refunds/{id}/cancel` | 取消退款（body: `{staffId}`） | 無限制 |
| PATCH | `/api/refunds/{id}/review` | 審核通過（body: `{reviewerId}`） | 僅主管，否則 403 |
| POST | `/api/refunds/import-charge-list` | 匯入收費清單試算（body: `{chargeListId}`），回傳 `baseAmount`/`deductionTotal` 供表單預填，不寫入資料庫 | 無限制 |

超額扣款產生的應收帳單改用既有的收費清單 API 編輯，無需新增 API：`GET/POST/PUT /api/charge-lists`（見 `ChargeListController`）。

---

## 6. 權限模型

- 前端登入時由 `AuthService.applyPermissions()`（後端）計算 `canReviewRefund`（角色為「主管」時為 `true`），寫入 `AuthUser` 並存進 `sessionStorage`，前端用 `canReviewRefund()` 決定「審核通過」按鈕是否顯示。
- 後端 `RefundService.reviewRefund()` 會**再次以 `reviewerId` 查詢該員工角色**驗證是否為「主管」，非主管回傳 403，避免單純依賴前端隱藏按鈕。

> ⚠️ **已知限制**：本系統目前沒有 session／JWT 等伺服器端身分驗證機制，後端是「信任前端傳來的 `staffId`／`reviewerId`」去查角色，這與系統既有的 `created_by`/`updated_by` 等欄位的信任模式一致，但嚴格來說**無法防止竄改請求偽造他人身分**。若未來要做到真正安全的權限控管，需要先在整個系統補上登入態驗證（session 或 JWT），而不只是這個功能單獨處理。

---

## 7. 前端操作流程

### 7.1 查詢列表
- 篩選列：公司名稱、統一編號、建立日期（起訖）、狀態下拉。
- 列表僅顯示 4 欄簡單資訊：客戶／統編、退款原因、退款金額、狀態。
- 點擊任一列會開啟「退款詳細資料」彈窗，顯示完整欄位（租約編號、調整金額/備註、扣款總額、建立人/時間、審核人/時間、退款方式、收款帳戶、退款日期），並依目前狀態顯示對應操作按鈕（修改／送交審核／審核通過／辦理退款／取消）。「送交審核」僅在狀態為「草稿」時出現，用來把草稿轉為「待審核」交給主管審核。

### 7.2 新增退款
1. 輸入公司名稱搜尋客戶並選擇。
2. 選擇該客戶的租約（下拉會顯示押金金額）。
3. （選填）輸入收費清單編號並按「帶入扣款金額」，自動預填扣款總額。
4. 填寫退款原因（必填）、調整金額／備註、扣款總額、退款方式與收款帳戶。
5. 選擇送出後狀態（「草稿」或「待審核」）並送出。
6. 若觸發超額扣款，畫面上方會顯示提示訊息（含自動建立的收費清單編號），需另外到「收費清單管理」畫面確認/收款。

### 7.3 修改／辦理退款
- 「草稿」「待審核」狀態：詳細彈窗按「修改」開啟編輯面板，可改動所有欄位。
- 「審核通過」狀態：按「辦理退款」開啟同一編輯面板，僅能填寫付款資訊與退款日期，金額欄位鎖定為唯讀。

---

## 8. 已知限制 / 待辦事項

- **權限驗證非真正的伺服器端身分驗證**：如第 6 節所述，審核 API 的角色檢查是信任前端傳入的 `reviewerId`，非登入態驗證。
- **收費清單狀態回壓有兩種情境不會自動處理**：詳見第 4.4 節（退款取消、以及編輯後不再超額扣款），皆需秘書自行到收費清單管理手動確認/修正。
- **押金金額為即時讀取，非鎖定快照**：計算退款金額時即時查詢 `contracts.deposit`，若之後押金被更改，既有退款單的金額不會自動重算，需手動修改。
- **匯入收費清單為唯讀試算**：不會鎖定或標記該收費清單已被使用，同一張收費清單可重複匯入到多筆退款單，也沒有防止重複請款的檢查。
- **沒有防止同一租約重複建立未完成退款單**：可以對同一租約同時存在多筆「草稿/待審核」退款。
- **應收帳單無法區分「一般月費」與「退款差額」**：由於刻意不新增欄位、共用 `charge_lists` 既有結構，只能靠 `fee_month IS NULL` 或反查 `refunds.charge_list_id` 間接判斷，收費清單列表上不會有特別標示。
- **前端沒有逐欄位的即時驗證訊息**：必填檢查多半仰賴後端回傳的 400 錯誤訊息（`branchApiErrorMessage` 顯示於畫面上方）。
- **測試覆蓋**：沒有新增針對退款的專屬單元測試，驗證方式為既有 `CmsApplicationTests`（H2 profile，涵蓋完整 schema migration 與 seed data 載入）＋ `ng build --strict templates` 前端型別檢查；未使用瀏覽器實際操作驗證 UI 互動細節。
