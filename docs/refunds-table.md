# refunds 資料表欄位說明

## 基本資訊

| 欄位名稱 | 資料型別 | 必填 | 說明 |
|----------|----------|------|------|
| `refund_id` | BIGINT | ✓ | 退款編號（主鍵） |
| `customer_id` | BIGINT | | 客戶編號（FK → customers） |
| `contract_id` | BIGINT | | 租約編號（FK → contracts） |
| `charge_list_id` | BIGINT | | 收費清單編號（FK → charge_lists） |
| `company_name` | VARCHAR(255) | | 公司名稱（非正規化快取欄位） |

## 退款原因

| 欄位名稱 | 資料型別 | 必填 | 說明 |
|----------|----------|------|------|
| `reason` | VARCHAR(500) | | 退款原因：`正常退租` / `中途解約` / `其他` |

## 金額計算

| 欄位名稱 | 資料型別 | 必填 | 說明 |
|----------|----------|------|------|
| `deduction_total` | DECIMAL(12,2) | | 扣款總金額（各項明細扣款加總） |
| `adjustment_amount` | DECIMAL(12,2) | | 手動調整金額（可正可負，預設 0） |
| `adjustment_note` | VARCHAR(1000) | | 手動調整金額備註（調整金額不為 0 時必填） |
| `refund_amount` | DECIMAL(12,2) | | 退款總金額（押金 − 扣款總金額 + 手動調整） |

## 退款方式

| 欄位名稱 | 資料型別 | 必填 | 說明 |
|----------|----------|------|------|
| `payment_method` | VARCHAR(20) | | 退款方式：`匯款` / `現金` |
| `bank_code` | VARCHAR(20) | | 收款銀行代碼（匯款時使用） |
| `bank_account` | VARCHAR(50) | | 收款帳號（匯款時使用） |
| `bank_account_name` | VARCHAR(100) | | 收款戶名（匯款時使用） |

## 人員與狀態

| 欄位名稱 | 資料型別 | 必填 | 說明 |
|----------|----------|------|------|
| `termination_staff_id` | BIGINT | | 解約職員編號（FK → staff） |
| `refund_status` | VARCHAR(20) | | 退款狀態：`待審核` / `已撥款` / `已作廢` |
| `refunded_at` | VARCHAR(30) | | 退款時間（撥款後押上的日期） |

## 建立資訊

| 欄位名稱 | 資料型別 | 必填 | 說明 |
|----------|----------|------|------|
| `created_by` | BIGINT | | 建立人（FK → staff） |
| `created_at` | TIMESTAMP | | 建立時間（自動填入） |

## 審核資訊

| 欄位名稱 | 資料型別 | 必填 | 說明 |
|----------|----------|------|------|
| `reviewed_by` | BIGINT | | 審核人（FK → staff） |
| `reviewed_at` | TIMESTAMP | | 審核時間 |

## 修改紀錄

| 欄位名稱 | 資料型別 | 必填 | 說明 |
|----------|----------|------|------|
| `updated_by` | BIGINT | | 最後修改人（FK → staff） |
| `updated_at` | TIMESTAMP | | 最後修改時間（自動更新） |
