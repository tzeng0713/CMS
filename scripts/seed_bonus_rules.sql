-- 業績獎金規則初始資料（8 種規則，數值依《獎金計算規則說明》PDF，
-- 與 backend/src/main/resources/data/seed-data.json 的 bonusRules 區塊內容一致）。
-- CMS_SEED_ENABLED=false 的正式環境不會自動跑這份資料，需要手動執行一次：
--   docker compose exec -T mysql mysql --default-character-set=utf8mb4 -uroot -p你的root密碼 cms < scripts/seed_bonus_rules.sql

INSERT INTO bonus_rules (bonus_rule_id, rule_name, rule_type, unit_amount, percentage, tier_config, period_type, description, is_active) VALUES
(1, '辦公室出租獎金', 'OFFICE_RENTAL', 2000, NULL, NULL, 'PER_TRANSACTION', '客戶簽約租期達半年以上，每成交一間辦公室發放；一人簽約全額，雙祕書合作各半。', TRUE),
(2, '公司登記業績獎金', 'COMPANY_REGISTRATION', 800, NULL, NULL, 'PER_TRANSACTION', '每成功簽約一間公司登記案件發放，歸屬簽約祕書。', TRUE),
(3, '同心獎金', 'TEAMWORK', 500, NULL, NULL, 'PER_TRANSACTION', '公司登記案件由不同祕書合作完成時，另發放給協助祕書。', TRUE),
(4, '滿租獎金', 'FULL_OCCUPANCY', 3000, NULL, NULL, 'MONTHLY', '分館當月辦公室全數出租、無空置時，分館內每位祕書各自領取全額。', TRUE),
(5, '工商代辦獎金', 'BUSINESS_AGENT', 800, NULL, NULL, 'PER_TRANSACTION', '客戶標記為代辦（is_agent=true）時，其名下「綁約中」合約於業績結算時自動發放給簽約祕書；亦保留手動新增作為備援／覆寫。', TRUE),
(6, '公司登記加乘獎金', 'REGISTRATION_MULTIPLIER', NULL, NULL, '[{"threshold":3,"amount":2000},{"threshold":6,"amount":4000},{"threshold":8,"amount":5000},{"threshold":9,"amount":7000}]', 'MONTHLY', '依祕書於當月完成的公司登記成交數量，套用最高一個級距發放，不重複疊加。', TRUE),
(7, '分館績效獎金', 'BRANCH_PERFORMANCE', 800, NULL, NULL, 'FOUR_MONTH', '依分館結算期間（每 4 個月）內（新簽約－解約）公司數 × 800 元計算，分館內每位祕書各自領取全額。', TRUE),
(8, '公司登記年繳獎金', 'ANNUAL_PAYMENT', NULL, 0.01, NULL, 'PER_TRANSACTION', '公司登記客戶選擇一年繳一次租金（付款週期 12 個月）且已於對帳管理入帳時，依實收租金的 1% 發放給簽約祕書。', TRUE);
