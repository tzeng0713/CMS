# charge_lists 資料表欄位說明

## 基本資訊

| 欄位名稱 | 資料型別 | 必填 | 說明 |
|----------|----------|------|------|
| `charge_list_id` | BIGINT | ✓ | 收費清單編號（主鍵） |
| `customer_id` | BIGINT | ✓ | 客戶編號（FK → customers，建立後不可修改） |
| `contract_id` | BIGINT | ✓ | 租約編號（FK → contracts，建立後不可修改） |

## 費用期間

| 欄位名稱 | 資料型別 | 必填 | 說明 |
|----------|----------|------|------|
| `fee_month` | CHAR(7) | ✓ | 費用月份（YYYY-MM），單一月份 |

## 金額明細

| 欄位名稱 | 資料型別 | 必填 | 說明 |
|----------|----------|------|------|
| `management_fee` | DECIMAL(12,2) | ✓ | 管理費（預設 0，不可為負數） |
| `electricity_fee` | DECIMAL(12,2) | ✓ | 電費（預設 0，不可為負數） |
| `printing_fee` | DECIMAL(12,2) | ✓ | 列印費（預設 0，不可為負數） |
| `meeting_room_fee` | DECIMAL(12,2) | ✓ | 會議室費（預設 0，不可為負數） |
| `tax` | DECIMAL(12,2) | ✓ | 營業稅（預設 0，不可為負數） |
| `advance_payment` | DECIMAL(12,2) | ✓ | 代墊費（預設 0，不可為負數） |
| `repair_fee` | DECIMAL(12,2) | ✓ | 修繕費（預設 0，不可為負數） |
| `total_amount` | DECIMAL(12,2) | ✓ | 收費總金額。前端不可送入，後端於寫入前依上述 7 項金額重新計算 |

## 狀態

| 欄位名稱 | 資料型別 | 必填 | 說明 |
|----------|----------|------|------|
| `status` | TINYINT | ✓ | 1=已結清、2=未結清。系統維護欄位，本模組不接受前端建立/修改，僅由收租、退款模組完成流程後回寫 |

## 建立與修改資訊

| 欄位名稱 | 資料型別 | 必填 | 說明 |
|----------|----------|------|------|
| `created_by` | BIGINT | | 開單人（FK → staff），建立時自動寫入 |
| `issued_at` | TIMESTAMP | | 開單時間，建立時自動寫入 |
| `updated_by` | BIGINT | | 最後修改人（FK → staff） |
| `updated_at` | TIMESTAMP | | 最後修改時間，`ON UPDATE CURRENT_TIMESTAMP` 自動更新 |

## Index

- `idx_charge_lists_customer_id` (`customer_id`)
- `idx_charge_lists_contract_id` (`contract_id`)
- `idx_charge_lists_status` (`status`)
- `idx_charge_lists_issued_at` (`issued_at`)
- `idx_charge_lists_fee_month` (`fee_month`)
