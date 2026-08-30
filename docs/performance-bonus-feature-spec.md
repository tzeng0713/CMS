# 業績管理功能規格書

本文件說明業績管理（業績目標／業績獎金規則／業績結算）功能的資料模型、業務邏輯、API 與前端操作流程，作為後續維護與驗收的依據。對應程式碼位置：

- 後端：`backend/src/main/java/com/example/cms/controller/{BonusRuleController,SalesTargetController,PerformanceBonusController}.java`、`.../service/{BonusRuleService,SalesTargetService,PerformanceBonusService}.java`
- 資料庫：`backend/src/main/resources/schema.sql`（`bonus_rules`、`performance_bonuses`、`sales_targets`）、`.../config/SchemaMigrationRunner.java`（`migratePerformanceManagementTables()`）、`.../config/SeedDataLoader.java`（`insertBonusRules()`）
- 前端：`frontend/src/app/app.component.ts`、`app.component.html`（`activeView() === 'targets' / 'bonus-rules' / 'performance-bonuses'` 區塊）、`frontend/src/app/core/cms-api.service.ts`

依據來源：使用者提供之《獎金計算規則說明》PDF，列出 8 種業績獎金規則。

---

## 1. 功能總覽

| 功能 | 說明 |
|---|---|
| 新增業績獎金規則（主管權限） | 定義獎金規則的名稱、類型、金額／比例／級距、是否啟用，作為結算計算的依據 |
| 新增當月業績目標（主管權限） | 依分館、月份、類別設定業績目標數量 |
| 查詢業績目標資料 | 依分館／月份／類別篩選查詢已設定的業績目標 |
| 查詢業績結算資料 | 依規則類型／期間／分館／祕書篩選查詢已結算的業績獎金明細 |
| 業績獎金結算（主管權限，三種自動觸發＋一種手動新增） | 依規則類型分為「逐筆合約／收款觸發」「按月觸發」「每 4 個月期間觸發」三種自動結算，皆可重複執行且不會對同一筆事件重複入帳；另有「手動新增」供沒有自動結算引擎的規則（目前僅規則五）登打單筆獎金 |

### 8 種業績獎金規則對照表

| # | 規則名稱 | rule_type | 是否自動結算 | 觸發方式 |
|---|---|---|---|---|
| 一 | 辦公室出租獎金 | `OFFICE_RENTAL` | ✅ | 逐筆合約（`sync-transactions`） |
| 二 | 公司登記業績獎金 | `COMPANY_REGISTRATION` | ✅ | 逐筆合約（`sync-transactions`） |
| 三 | 同心獎金 | `TEAMWORK` | ✅ | 逐筆合約（`sync-transactions`） |
| 四 | 滿租獎金 | `FULL_OCCUPANCY` | ✅ | 按月（`settle-monthly`） |
| 五 | 工商代辦獎金 | `BUSINESS_AGENT` | ❌ 無自動結算引擎 | 手動新增（`POST /api/performance-bonuses/manual`，主管限定） |
| 六 | 公司登記加乘獎金 | `REGISTRATION_MULTIPLIER` | ✅ | 按月（`settle-monthly`，與規則四同一入口同時觸發） |
| 七 | 分館績效獎金 | `BRANCH_PERFORMANCE` | ✅ | 4 個月期間（`settle-period`） |
| 八 | 公司登記年繳獎金 | `ANNUAL_PAYMENT` | ✅ | 逐筆收款（`sync-transactions`） |

**規則五（工商代辦獎金）沒有自動結算引擎**：探索確認系統裡完全沒有區分「公司登記」（登記地址服務）與「工商代辦」（設立登記送件）的資料欄位——兩者在 `contracts` 表裡都只是 `rental_item = '登記'` 的同一筆合約，且系統沒有「案件已完成送件流程」狀態欄位。若強行自動計算會與規則二（公司登記業績獎金）對同一張合約重複發放。

已向廠商確認實際運作方式，印證上述判斷：公司登記是持續性服務、有簽約；工商代辦是單次代辦服務，**沒有另外簽訂服務合約**，因此系統確實無法從 `contracts` 表區分兩者。目前實際流程是：案件完成、業績確認後，歸屬的祕書會把資料填寫在「新簽約檔案」（公司內部維護的紀錄，非本系統的表，CMS 完全沒有對應的資料結構）；每月計算薪資時，由主管／薪資處理人員依「新簽約檔案」核對後計算該筆獎金。對應本系統的功能是：主管在核對後，透過 `POST /api/performance-bonuses/manual` 手動登打單筆獎金（見第 5 節「規則五」）；「新簽約檔案」本身不在 CMS 範圍內，這次不建表。

**規則六（公司登記加乘獎金）從「每 4 個月結算一次」改成「按月結算」**：原規格書依《獎金計算規則說明》PDF 寫的是「累積結算期間內」，一開始比照規則七理解成 4 個月一期；已向廠商確認累積範圍其實就是當月，不是 4 個月，因此改成跟規則四同一個入口（`settle-monthly`）按月觸發。既有以 `YYYY-P{1-3}` 格式入帳的規則六歷史資料，因為代表的是「4 個月累積算出的一個門檻結果」，沒有辦法精準拆回某一個月份（系統未保留當時逐月的合約明細快照），已與業主確認直接刪除、不轉換，後續全部用月結重新產生（見 `SchemaMigrationRunner.migrateRegistrationMultiplierToMonthly()`）。

---

## 2. 資料表

### 2.1 新表：`bonus_rules`（業績獎金規則）

| 欄位 | 型別 | 說明 |
|---|---|---|
| `bonus_rule_id` | BIGINT | 主鍵 |
| `rule_name` | VARCHAR(100) | 規則名稱，必填 |
| `rule_type` | VARCHAR(50) | 上表 8 種固定值之一，Java 端驗證 |
| `unit_amount` | DECIMAL(12,2) | 單位金額；規則一（2000，依人數均分）、二（800）、三（500）、四（3000，每人全額）、七（800，分館內每人全額）使用 |
| `percentage` | DECIMAL(6,4) | 比例；規則八使用（如 0.01 = 1%），套用在對應 `rent_payments.amount` |
| `tier_config` | VARCHAR(1000) | JSON 字串，規則六使用，格式 `[{"threshold":3,"amount":2000}, ...]` |
| `period_type` | VARCHAR(20) | 結算週期說明文字（`PER_TRANSACTION`／`MONTHLY`／`FOUR_MONTH`），僅供顯示參考，實際判斷邏輯寫死在對應 Service 方法 |
| `description` | VARCHAR(500) | 說明文字 |
| `is_active` | BOOLEAN | 是否啟用；結算引擎只認得啟用中的規則 |
| `created_by`／`created_at`／`updated_by`／`updated_at` | — | 稽核欄位 |

### 2.2 既有表擴充：`performance_bonuses`（泛化為通用業績獎金明細表）

新增欄位：`bonus_rule_id`、`rule_type`、`contract_id`、`branch_id`、`rent_payment_id`、`signed_count`、`cancelled_count`、`note`、`created_by`、`created_at`（既有 `bonus_id, staff_id, period, net_count, bonus_amount` 不動，皆可為 NULL，不影響舊 seed 資料）。

各規則對這張表的用法：

| rule_type | contract_id | branch_id | rent_payment_id | period 格式 | signed/cancelled_count |
|---|---|---|---|---|---|
| `OFFICE_RENTAL`（一） | 有 | — | — | `YYYY-MM`（合約簽約日換算） | — |
| `COMPANY_REGISTRATION`（二） | 有 | — | — | `YYYY-MM`（合約簽約日換算） | — |
| `TEAMWORK`（三） | 有（同二） | — | — | `YYYY-MM`（合約簽約日換算） | — |
| `FULL_OCCUPANCY`（四） | — | 有 | — | `YYYY-MM` | — |
| `REGISTRATION_MULTIPLIER`（六） | — | — | — | `YYYY-MM` | — |
| `BRANCH_PERFORMANCE`（七） | — | 有 | — | `YYYY-P{1-3}` | 有 |
| `ANNUAL_PAYMENT`（八） | 有 | — | 有 | `YYYY-MM`（收款匯款日換算） | — |

`rent_payment_id` 僅規則八使用，作為冪等性判斷鍵與追溯依據（FK 指向 `rent_payments`）。規則一／二／三／八的 `period` 是結算當下才換算補上的（見第 5 節），冪等性判斷鍵不變（仍是 `contract_id`／`rent_payment_id`），純粹是多存一個月份歸屬供查詢彙總用；日期缺漏或格式無法解析時該筆會被跳過、不入帳（計入 `sync-transactions` 回傳的 `skippedMissingDate`）。

### 2.3 既有表擴充：`sales_targets`

新增稽核欄位 `created_by`、`created_at`。既有 `sales_target_id, branch_id, target_month, category, target_count` 不動。

### 2.4 沒有新增的表／欄位

- **沒有新增「案件完成流程」狀態欄位**：規則五（工商代辦獎金）需要，但這次不實作，待後續設計。
- **沒有新增「付款頻率」欄位**：原以為規則八（公司登記年繳獎金）需要，後確認可直接沿用既有 `contracts.payment_months`（付款週期）判斷是否為年繳，不需新欄位。

---

## 3. 分館歸屬判斷邏輯

規則四（滿租獎金）與規則七（分館績效獎金）都需要「合約屬於哪個分館」。合約透過 `contracts.office_id → offices.branch_id` 取得分館，但「登記」類型合約常見 `office_id` 為 NULL（沒有實體辦公室）。因此凡涉及分館歸屬的查詢一律用：

```sql
COALESCE(o.branch_id, st.branch_id)
-- o = offices（透過 contracts.office_id），st = staff（透過 contracts.signer_staff_id）
```

即：優先用辦公室的分館；沒有辦公室時退回用**簽約人自己所屬的分館**。仍然無法判斷分館的合約（沒有 office 也沒有 signer 或 signer 沒有 `branch_id`）會被排除計算，並在規則七的結算回應中以 `unassignedContractCount` 呈現數量供主管參考，不阻擋整體結算。

---

## 4. 期間格式定義

- **`YYYY-MM`**：規則一、二、三、四、六、八使用，一般月份格式，對應 `<input type="month">`。規則四、六是「結算當月」的期間值；規則一、二、三、八是「結算時依合約簽約日／收款匯款日換算」出來的月份歸屬值。
- **`YYYY-P{1|2|3}`**：僅規則七使用，對應「每 4 個月結算一次，一年三次」——P1 = 1～4 月、P2 = 5～8 月、P3 = 9～12 月。與既有 seed 資料裡 `performance_bonuses.period = "2026-Q2"`（季度格式）並存，不修改舊資料；新結算一律採本格式。規則六原本也用這個格式，改成按月結算後舊資料已刪除（見第 1 節）。

---

## 5. 各規則計算邏輯

### 規則一：辦公室出租獎金（`OFFICE_RENTAL`，逐筆合約觸發，併入 `sync-transactions`）
- 條件：`rental_item='辦公室'` 且 `lease_status='綁約中'`，且租期 ≥ 6 個月。租期用 `start_date_text`／`end_date_text` 解析成 `LocalDate` 後計算（**不用 `payment_months`**——該欄位是「付款週期」非「總租期」，已由 seed 資料驗證：例如租期 27 天的合約 `payment_months=12`）。`start_date_text`／`end_date_text` 任一為空或無法解析則略過，計入 `skippedMissingDate`。
- 金額：`unit_amount`（規則設定 2000）。有 `partner_staff_id` 且與 `signer_staff_id` 不同時，signer、partner 各得一半；否則 signer 得全額。
- `period`：`start_date_text` 換算的 `YearMonth`（`YYYY-MM`）。
- 冪等性：`contract_id` 已有 `OFFICE_RENTAL` 紀錄則跳過。

### 規則二：公司登記業績獎金（`COMPANY_REGISTRATION`，逐筆合約觸發，併入 `sync-transactions`）
- 條件：以登記合約成立（簽約）為唯一判斷依據——`rental_item='登記'` 且 `lease_status='綁約中'`，不需其他案件流程狀態。`start_date_text` 無法解析則整筆合約（含規則三）略過，計入 `skippedMissingDate`。
- 金額：`unit_amount`（800）全額給 `signer_staff_id`。
- `period`：`start_date_text` 換算的 `YYYY-MM`。
- 冪等性：`contract_id` 已有 `COMPANY_REGISTRATION` 紀錄則跳過。

### 規則三：同心獎金（`TEAMWORK`，與規則二同一批合約掃描）
- 條件：規則二合約中，`partner_staff_id` 存在且與 `signer_staff_id` 不同。
- 金額：`unit_amount`（500）給 `partner_staff_id`，**不影響**規則二 signer 的 800，兩者分別入帳。
- `period`：與規則二同一筆合約共用同一個 `start_date_text` 換算的 `YYYY-MM`。
- 冪等性：`contract_id` 已有 `TEAMWORK` 紀錄則跳過。

### 規則四：滿租獎金（`FULL_OCCUPANCY`，按月觸發，`settle-monthly`）
- 對指定 `yearMonth`、每個分館：`totalOffices` = 該分館辦公室總數；`occupiedOffices` = 該分館底下、在**月底時點**仍有效的合約所覆蓋的相異辦公室數（`lease_status='綁約中'` 且 `start_date_text ≤ 月底` 且 `termination_date_text` 為空或晚於月底）。此為「月底時點快照」判斷，非「整月每一天都滿租」。
- 若 `totalOffices > 0` 且 `occupiedOffices == totalOffices` → 全滿租 → `unit_amount`（3000）給分館內每位祕書**各自一筆全額**。
- 冪等性：`branch_id` + `period=YYYY-MM` 已有 `FULL_OCCUPANCY` 紀錄則跳過整個分館。

### 規則六：公司登記加乘獎金（`REGISTRATION_MULTIPLIER`，按月觸發，與規則四同一入口 `settle-monthly`）
- 對指定 `yearMonth`（同一個月份，跟規則四共用同一次請求）：依 `signer_staff_id` 分組，統計**當月** `start_date_text` 落在該月份、`rental_item='登記'` 的合約數（逐人累計，非逐筆給錢）。
- 依累計數套用 `tier_config` 找「數量 ≤ 累計數」中門檻最高的一級（不重複疊加）；累計數 < 3 不產生紀錄。
- `period`：與規則四同一個 `yearMonth`。
- 冪等性：`staff_id` + `period` 已有 `REGISTRATION_MULTIPLIER` 紀錄則跳過。
- `settleMonthly()` 回傳 `fullOccupancyCreatedCount`／`registrationMultiplierCreatedCount` 分開統計，讓前端能分別顯示兩條規則各自新增的筆數。

### 規則七：分館績效獎金（`BRANCH_PERFORMANCE`，4 個月期間觸發，`settle-period`）
- 對指定 `period`（`YYYY-P{1-3}`）：依「分館歸屬邏輯」統計每個分館的 `signedCount`（`start_date_text` 落在區間，**全部租約類型**）與 `cancelledCount`（`termination_date_text` 落在區間）。
- `netCount = signedCount - cancelledCount`（**允許負數，不 floor at 0**）；`totalBonus = netCount × unit_amount`（800）。
- **分館內每位祕書各自領取 `totalBonus` 全額**（比照規則四的分配方式，不再平分）；分館無祕書則跳過並記錄在 `skippedBranches`。
- 冪等性：`branch_id` + `period` 已有 `BRANCH_PERFORMANCE` 紀錄則跳過。

### 規則八：公司登記年繳獎金（`ANNUAL_PAYMENT`，逐筆收款觸發，併入 `sync-transactions`）
- 「年繳」判斷：`contracts.rental_item='登記'` 且 `contracts.payment_months = 12`（付款週期＝12 個月＝一年繳一次）。`payment_months` 沒有資料庫層級的允許值限制（純自由整數），此為本功能依現有資料表能力做的判斷邏輯，非系統強制保證所有年繳合約都填 12。
- 「已入帳」判斷：透過**對帳管理**既有的 `rent_payments` 表——該登記合約存在一筆 `payment_date_text` 已填寫（非空）的收款紀錄，即視為已入帳（系統裡沒有獨立的發票系統，`rent_payments` 的存在本身就是收款紀錄）。`payment_date_text` 無法解析則略過，計入 `skippedMissingDate`。
- 金額：`rent_payments.amount × percentage`（`bonus_rules` 中 `ANNUAL_PAYMENT` 規則的 `percentage`）。
- 歸屬：該合約的 `signer_staff_id`。
- `period`：**用收款的 `payment_date_text`（匯款日）換算，不是合約簽約日**。
- 冪等性：`rent_payment_id` 已有 `ANNUAL_PAYMENT` 紀錄則跳過（同一筆收款只計算一次；同一合約日後再繳一次年租金，是新的 `rent_payment_id`，會再算一次）。

### 規則五：工商代辦獎金（`BUSINESS_AGENT`，手動新增）
- 沒有自動結算引擎（原因見第 1 節）。規則本身仍可透過「新增業績獎金規則」建立設定（`unitAmount`，供人工參考金額）。
- 實際入帳走新端點 `POST /api/performance-bonuses/manual`（`PerformanceBonusService.manualCreate()`）：主管在業績結算頁的「結算觸發」面板第四區塊，選祕書、選規則、填金額（可參考規則的 `unit_amount`，但金額本身可自由調整）、選填期間與備註（用來記錄案件／客戶說明，因為系統沒有工商代辦案件的資料表可關聯），送出後直接寫入 `performance_bonuses`。
- **只能選擇 `MANUALLY_ONLY_RULE_TYPES` 白名單內的規則類型**（目前僅 `BUSINESS_AGENT`）：後端會擋掉對已有自動結算引擎的規則類型（一/二/三/四/六/七/八）手動新增，避免跟 `sync-transactions`／`settle-monthly`／`settle-period` 重複入帳；前端下拉選單也只列出白名單內、啟用中的規則。
- **沒有冪等性檢查**：跟其他規則不同，這是人工登打的單筆資料，系統不會比對是否重複，需要靠主管自己核對「新簽約檔案」避免同一案件重複輸入。
- 權限：跟其他結算動作一樣，透過 `requireManager()` 重新驗證請求者（`staffId`）必須是「主管」，跟實際獲得獎金的祕書（`beneficiaryStaffId`）是分開的兩個欄位。

---

## 6. 主管權限重驗證

所有寫入動作（新增業績獎金規則、新增業績目標、三種結算觸發）皆比照 `RefundService.reviewRefund()` 的既有慣例：後端不信任前端的權限旗標，改用請求裡的 `staffId` 重新查 `staff → role_permissions.role_name`，非「主管」一律回傳 `403 Forbidden`。前端 `canManageBonusRules()`（對應 `AuthUser.canManageBonusRules`，由 `AuthService.applyPermissions()` 依 `role_name === '主管'` 計算）僅用於控制表單／按鈕的顯示，不作為安全邊界。

---

## 7. API 一覽

| Method | Path | 說明 |
|---|---|---|
| GET | `/api/bonus-rules` | 查詢所有業績獎金規則（不分權限） |
| POST | `/api/bonus-rules` | 新增業績獎金規則（主管限定，body 含 `staffId`） |
| PUT | `/api/bonus-rules/{id}` | 修改業績獎金規則（主管限定，驗證規則與新增相同；規則不存在回 404） |
| GET | `/api/sales-targets?branchId=&targetMonth=&category=&page=&pageSize=` | 分頁查詢業績目標 |
| POST | `/api/sales-targets` | 新增業績目標（主管限定，body 含 `staffId`；同分館＋月份＋類別重複會擋） |
| GET | `/api/performance-bonuses?ruleType=&period=&branchId=&staffId=&contractId=&page=&pageSize=` | 分頁查詢業績結算明細 |
| POST | `/api/performance-bonuses/sync-transactions` | 結算規則一二三八（逐筆合約／收款觸發，body：`{staffId}`） |
| POST | `/api/performance-bonuses/settle-monthly` | 結算規則四＋規則六（同一入口同時觸發，body：`{yearMonth, staffId}`，`yearMonth` 格式 `YYYY-MM`） |
| POST | `/api/performance-bonuses/settle-period` | 結算規則七（body：`{period, staffId}`，`period` 格式 `YYYY-P1`/`YYYY-P2`/`YYYY-P3`） |
| POST | `/api/performance-bonuses/manual` | 手動新增單筆業績獎金（主管限定，僅限規則五等無自動結算引擎的規則；body：`{staffId, beneficiaryStaffId, bonusRuleId, amount, period?, note?}`，`staffId` 為送出者、`beneficiaryStaffId` 為獎金歸屬祕書） |

`sync-transactions` 回傳 `{createdCount, skippedAlreadyRecorded, skippedMissingDate, skippedNoActiveRule}`；`settle-monthly` 回傳 `{period, fullOccupancyCreatedCount, registrationMultiplierCreatedCount, skippedBranches}`（`skippedBranches` 只跟規則四「分館無祕書」有關）；`settle-period` 回傳 `{period, createdCount, skippedBranches, unassignedContractCount}`；`manual` 回傳新增的 `performance_bonuses` 完整資料（含 join 後的 `staff_name`／`rule_name`）。

---

## 8. 前端操作流程

- 導覽列新增「業績管理」分組，含「業績目標」「業績獎金規則」「業績結算」三個頁籤（原「業績目標」從「其他查詢」分組移過來）。
- **業績目標**頁：篩選＋列表＋新增表單（主管限定）。
- **業績獎金規則**頁：規則列表（`BUSINESS_AGENT` 顯示「尚未支援自動結算」提示）＋新增／修改表單（主管限定，依規則類型填寫 `unitAmount`／`percentage`／`tierConfig` 其中之一）：
  - `periodType`（結算週期）為下拉選單（`bonusRulePeriodTypeOptions`），選項與中文標籤為「逐筆合約／收款觸發」`PER_TRANSACTION`、「按月結算」`MONTHLY`、「每 4 個月期間結算」`FOUR_MONTH`；規則列表的「結算週期」欄位也用同一份對照表（`bonusRulePeriodTypeLabel()`）顯示中文，不顯示原始代碼。
  - `tierConfig`（規則六專用）不再是原始 JSON 輸入框，改為「達成數量門檻／獎金金額」成對輸入列，可用「新增級距」／「刪除」增減列數；既有資料由 `parseBonusRuleTiers()` 解析回填，送出前由 `serializeBonusRuleTiers()` 依門檻由小到大排序、過濾空列後組回 `[{"threshold":n,"amount":n}, ...]` 字串，資料庫欄位格式與後端驗證邏輯不變。
  - 規則列表改為專屬的 `bonus-rule-row` 版面（不再用內部固定高度捲動的通用表格），並補上手機窄螢幕下的單欄卡片樣式，桌面與手機皆不會出現多餘捲軸。
- **業績結算**頁：
  - 頁面最上方「各祕書可獲得獎金一覽」卡片區：依目前的規則類型／期間／分館篩選條件（不含祕書篩選、不受分頁影響，`pageSize=200` 一次取回加總）在前端依 `staff_name` 分組加總 `bonus_amount`，依金額由高到低排序，讓主管一眼看出每位祕書的獎金總額與筆數；此彙總為前端計算，未新增後端彙總 API。涵蓋全部 8 種規則。
  - **結算觸發**面板（主管限定，三個自動觸發＋一個手動新增）：「同步逐筆獎金」按鈕（規則一二三八）、「月結」按鈕＋月份選擇器（規則四＋規則六同時觸發）、「期間結算」按鈕＋年份／期別選擇器（規則七）、「手動新增業績獎金」表單（規則五等無自動結算引擎的規則；祕書＋業績獎金規則下拉，下拉只列出 `MANUAL_ONLY_RULE_TYPES` 白名單內、啟用中的規則＋金額＋期間（選填）＋備註（選填，供填寫案件／客戶說明））。送出後於畫面顯示 API 回傳的 `skipped*`／`unassigned*` 提示文字或成功訊息；「月結」的訊息會分別列出滿租獎金與登記加乘獎金各自新增的筆數。
  - **規則四／六／七三個固定區塊**（`bonus-block-section`）：取代原本「靠篩選器切換規則類型查看同一張清單」的做法，改成三個並排的固定面板，各自獨立抓資料（`loadFullOccupancyBlock()`／`loadRegistrationMultiplierBlock()`／`loadBranchPerformanceBlock()`），不用手動切換規則類型篩選器：
    - 「滿租獎金（月結）」「登記加乘獎金（月結）」共用同一個「查詢月份」`<input type="month">`（`bonusBlockMonth`），標題顯示「YYYY年MM月 業績」（`monthLabel()`）。
    - 「分館績效獎金（期間結）」有自己的「年度」＋「期別」篩選（`bonusBlockPeriodYear`／`bonusBlockPeriodQuarter`），標題顯示「YYYY年 P{1/2/3} 期業績（對應月份區間）」（`quarterLabel()`）；明細表多兩欄「簽約數」「解約數」「淨數」「每人獎金金額」（直接讀 `signed_count`／`cancelled_count`／`net_count`／`bonus_amount`，同分館內每筆都一樣，取第一筆即可），另一欄列出實際領取的祕書名單。
    - 每個區塊內都先用 `groupPerformanceBonusesByBranch()`（純函式）依分館分組顯示明細表，再用 `summarizeByStaff()`（純函式，`loadStaffBonusSummary()` 也共用同一份邏輯）算出該區塊自己的祕書彙總卡片（`.kpis.staff-bonus-kpis`），三區塊彙總各自獨立、不合併成跨規則的一份。
    - 「月結」「期間結算」按鈕觸發成功後，會分別重新載入對應的區塊（月結重載滿租＋登記加乘；期間結算重載分館績效）。
  - 頁面下半部原本的篩選式清單（規則類型／期間／分館／祕書篩選＋扁平表格＋分頁）**維持不動**，仍涵蓋全部 8 種規則，作為原始明細查詢／稽核用途，跟新的三區塊是疊加關係、不是取代關係。

---

## 9. 已知限制／待補事項

- **「工商代辦獎金」已支援主管手動新增（`POST /api/performance-bonuses/manual`），但仍無自動結算**：依賴主管人工核對「新簽約檔案」（系統外部檔案）後登打，系統不做重複輸入檢查。若後續要做自動化，仍需要先設計並新增「案件完成流程」狀態欄位（目前系統完全沒有此概念），才能區分「公司登記」與「工商代辦」回補自動計算。
- **「分館績效獎金」「滿租獎金」的獎金歸屬對象皆以 `staff.branch_id` 找出分館所有祕書，分配方式相同**：皆為「分館內每人各自領取全額」，不平分（規則七原本平分，已依業主確認改成比照規則四）。PDF 原文未明確定義「管理祕書」對應哪個資料庫欄位，此為與需求方逐一確認後的設計決策。
- **「公司登記年繳獎金」用 `contracts.payment_months = 12` 判斷年繳**：`payment_months` 沒有資料庫層級的允許值限制，不保證所有「年繳」合約都確實填 12；若日後付款週期欄位改版或另建結構化的「付款頻率」欄位，需要回頭調整此判斷邏輯。
- **沒有排程機制**：三個結算動作（`sync-transactions`／`settle-monthly`／`settle-period`）皆須主管手動觸發，系統不會自動、定期執行（沿用國稅局通報功能的既有模式，專案目前無 `@Scheduled`/cron）。
- **規則七淨數可為負值，不做 floor at 0**：分館解約數大於新簽約數時，每人獎金金額會是負數。
- **已結算的（分館/祕書/合約/收款, 規則, 期間）組合會被冪等性檢查擋掉重複結算，但沒有「取消結算」API**：若結算資料有誤，需直接調整資料庫，畫面上無法復原重算。
- **規則六舊格式歷史資料已於這次改版時刪除**：`YYYY-P{1-3}` 格式的規則六紀錄（代表 4 個月累積結果，無法還原成單一月份）已透過 `SchemaMigrationRunner.migrateRegistrationMultiplierToMonthly()` 在啟動時一次性清除，不會出現在新的月結資料裡；若正式環境上線前已經有累積這類舊資料，同一支遷移程式會在部署後第一次啟動時自動清掉。
- **規則四採「月底時點快照」判斷是否全滿租**：非「整月每一天都滿租」，若辦公室在月中曾短暫空置但月底已補上，仍視為滿租。
- **沒有專屬單元測試**：驗證方式為手動以 H2 seed data 啟動後端，以 `curl` 呼叫各 API 確認回傳內容、冪等性與權限行為正確；前端以 `ng build` 確認型別檢查通過；未納入自動化測試套件。
- **「各祕書可獲得獎金一覽」是前端彙總，非後端 API**：一次取回 `pageSize=200`（`GET /api/performance-bonuses` 允許的上限）筆資料在瀏覽器端加總，若同一組篩選條件下的結算明細超過 200 筆，彙總金額會不完整；後續若資料量成長，應改為後端提供依祕書分組加總的專屬端點。
