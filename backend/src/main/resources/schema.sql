CREATE TABLE IF NOT EXISTS role_permissions (
  role_permission_id BIGINT PRIMARY KEY,
  role_name VARCHAR(50) NOT NULL,
  scope VARCHAR(100)
);

CREATE TABLE IF NOT EXISTS branches (
  branch_id BIGINT PRIMARY KEY,
  branch_name VARCHAR(100) NOT NULL,
  company_name VARCHAR(100),
  branch_code VARCHAR(50),
  branch_address VARCHAR(255),
  tax_id VARCHAR(30),
  bank_account VARCHAR(50),
  bank_branch VARCHAR(100),
  bank_account_name VARCHAR(100)
);

CREATE TABLE IF NOT EXISTS offices (
  office_id BIGINT PRIMARY KEY,
  office_no VARCHAR(50),
  branch_id BIGINT NOT NULL,
  notes VARCHAR(500),
  CONSTRAINT fk_offices_branch FOREIGN KEY (branch_id) REFERENCES branches(branch_id)
);

CREATE TABLE IF NOT EXISTS office_contacts (
  office_contact_id BIGINT PRIMARY KEY,
  office_id BIGINT NOT NULL,
  person_name VARCHAR(100),
  phone VARCHAR(80),
  CONSTRAINT fk_office_contacts_office FOREIGN KEY (office_id) REFERENCES offices(office_id)
);

CREATE TABLE IF NOT EXISTS staff (
  staff_id BIGINT PRIMARY KEY,
  role_permission_id BIGINT NOT NULL,
  branch_id BIGINT DEFAULT 1,
  staff_name VARCHAR(80) NOT NULL,
  account VARCHAR(80) NOT NULL,
  password_hash VARCHAR(255),
  CONSTRAINT fk_staff_role FOREIGN KEY (role_permission_id) REFERENCES role_permissions(role_permission_id),
  CONSTRAINT fk_staff_branch FOREIGN KEY (branch_id) REFERENCES branches(branch_id)
);

CREATE TABLE IF NOT EXISTS customers (
  customer_id BIGINT PRIMARY KEY,
  company_name VARCHAR(255) NOT NULL,
  tax_id VARCHAR(30),
  status TINYINT DEFAULT 0,
  rental_item VARCHAR(30),
  rental_status TINYINT DEFAULT 1,
  owner_name VARCHAR(100),
  owner_birthday VARCHAR(30),
  contact_person VARCHAR(100),
  contact_birthday VARCHAR(30),
  phone VARCHAR(100),
  forwarding_address VARCHAR(255),
  petty_cash DECIMAL(12,2),
  referrer VARCHAR(100),
  accountant_info VARCHAR(500),
  account_info VARCHAR(1000),
  is_agent BOOLEAN DEFAULT FALSE,
  notes VARCHAR(1000),
  registration_type VARCHAR(60),
  updated_by BIGINT,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_customers_updated_by FOREIGN KEY (updated_by) REFERENCES staff(staff_id)
);

CREATE TABLE IF NOT EXISTS customer_relation_groups (
  relation_group_id BIGINT PRIMARY KEY,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS customer_relation_members (
  relation_member_id BIGINT PRIMARY KEY,
  relation_group_id BIGINT NOT NULL,
  customer_id BIGINT,
  company_name VARCHAR(255) NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uq_relation_member_customer UNIQUE (customer_id),
  CONSTRAINT uq_relation_member_name UNIQUE (relation_group_id, company_name),
  CONSTRAINT fk_relation_members_group FOREIGN KEY (relation_group_id) REFERENCES customer_relation_groups(relation_group_id),
  CONSTRAINT fk_relation_members_customer FOREIGN KEY (customer_id) REFERENCES customers(customer_id)
);

CREATE TABLE IF NOT EXISTS contracts (
  contract_id BIGINT PRIMARY KEY,
  customer_id BIGINT NOT NULL,
  office_id BIGINT,
  rental_item VARCHAR(30),
  rental_status VARCHAR(30),
  signed_date_text VARCHAR(30),
  signer_staff_id BIGINT,
  partner_staff_id BIGINT,
  source_text VARCHAR(500),
  payment_months INT,
  start_date_text VARCHAR(30),
  end_date_text VARCHAR(30),
  termination_date_text VARCHAR(30),
  rent DECIMAL(12,2),
  deposit DECIMAL(12,2),
  registration_type VARCHAR(60),
  lease_status VARCHAR(30),
  lease_image_path VARCHAR(500),
  updated_by BIGINT,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_contracts_customer FOREIGN KEY (customer_id) REFERENCES customers(customer_id),
  CONSTRAINT fk_contracts_office FOREIGN KEY (office_id) REFERENCES offices(office_id),
  CONSTRAINT fk_contracts_signer_staff FOREIGN KEY (signer_staff_id) REFERENCES staff(staff_id),
  CONSTRAINT fk_contracts_partner_staff FOREIGN KEY (partner_staff_id) REFERENCES staff(staff_id),
  CONSTRAINT fk_contracts_updated_by FOREIGN KEY (updated_by) REFERENCES staff(staff_id)
);

CREATE TABLE IF NOT EXISTS rent_payments (
  rent_payment_id BIGINT PRIMARY KEY,
  customer_id BIGINT NOT NULL,
  contract_id BIGINT NOT NULL,
  payment_month INT,
  payment_date_text VARCHAR(30),
  fee_start_date_text VARCHAR(30),
  fee_end_date_text VARCHAR(30),
  amount DECIMAL(12,2),
  receipt_no VARCHAR(80),
  note VARCHAR(500),
  updated_by BIGINT,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_rent_payments_customer FOREIGN KEY (customer_id) REFERENCES customers(customer_id),
  CONSTRAINT fk_rent_payments_contract FOREIGN KEY (contract_id) REFERENCES contracts(contract_id),
  CONSTRAINT fk_rent_payments_updated_by FOREIGN KEY (updated_by) REFERENCES staff(staff_id)
);

CREATE TABLE IF NOT EXISTS charge_lists (
  charge_list_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  customer_id BIGINT NOT NULL,
  contract_id BIGINT NOT NULL,
  fee_month CHAR(7),
  management_fee DECIMAL(12,2) NOT NULL DEFAULT 0,
  electricity_fee DECIMAL(12,2) NOT NULL DEFAULT 0,
  printing_fee DECIMAL(12,2) NOT NULL DEFAULT 0,
  meeting_room_fee DECIMAL(12,2) NOT NULL DEFAULT 0,
  tax DECIMAL(12,2) NOT NULL DEFAULT 0,
  advance_payment DECIMAL(12,2) NOT NULL DEFAULT 0,
  repair_fee DECIMAL(12,2) NOT NULL DEFAULT 0,
  total_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
  status TINYINT NOT NULL DEFAULT 2,
  created_by BIGINT,
  issued_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_by BIGINT,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_charge_lists_customer FOREIGN KEY (customer_id) REFERENCES customers(customer_id),
  CONSTRAINT fk_charge_lists_contract FOREIGN KEY (contract_id) REFERENCES contracts(contract_id),
  CONSTRAINT fk_charge_lists_created_by FOREIGN KEY (created_by) REFERENCES staff(staff_id),
  CONSTRAINT fk_charge_lists_updated_by FOREIGN KEY (updated_by) REFERENCES staff(staff_id)
);

CREATE TABLE IF NOT EXISTS refunds (
  refund_id BIGINT PRIMARY KEY,
  customer_id BIGINT,
  contract_id BIGINT,
  charge_list_id BIGINT,
  company_name VARCHAR(255),
  refund_reason VARCHAR(500),
  adjustment_amount DECIMAL(12,2) DEFAULT 0,
  adjustment_note VARCHAR(1000),
  deduction_total DECIMAL(12,2),
  refund_amount DECIMAL(12,2),
  refund_status VARCHAR(20),
  payment_method VARCHAR(20),
  bank_code VARCHAR(20),
  bank_account VARCHAR(50),
  bank_account_name VARCHAR(100),
  refunded_at VARCHAR(30),
  termination_staff_id BIGINT,
  created_by BIGINT,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  reviewed_by BIGINT,
  reviewed_at TIMESTAMP,
  updated_by BIGINT,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_refunds_customer FOREIGN KEY (customer_id) REFERENCES customers(customer_id),
  CONSTRAINT fk_refunds_contract FOREIGN KEY (contract_id) REFERENCES contracts(contract_id),
  CONSTRAINT fk_refunds_charge_list FOREIGN KEY (charge_list_id) REFERENCES charge_lists(charge_list_id),
  CONSTRAINT fk_refunds_termination_staff FOREIGN KEY (termination_staff_id) REFERENCES staff(staff_id),
  CONSTRAINT fk_refunds_created_by FOREIGN KEY (created_by) REFERENCES staff(staff_id),
  CONSTRAINT fk_refunds_reviewed_by FOREIGN KEY (reviewed_by) REFERENCES staff(staff_id),
  CONSTRAINT fk_refunds_updated_by FOREIGN KEY (updated_by) REFERENCES staff(staff_id)
);

CREATE TABLE IF NOT EXISTS sales_targets (
  sales_target_id BIGINT PRIMARY KEY,
  branch_id BIGINT NOT NULL,
  target_month INT,
  category VARCHAR(50),
  target_count INT,
  created_by BIGINT,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_sales_targets_branch FOREIGN KEY (branch_id) REFERENCES branches(branch_id),
  CONSTRAINT fk_sales_targets_created_by FOREIGN KEY (created_by) REFERENCES staff(staff_id)
);

CREATE TABLE IF NOT EXISTS bonus_rules (
  bonus_rule_id BIGINT PRIMARY KEY,
  rule_name VARCHAR(100) NOT NULL,
  rule_type VARCHAR(50) NOT NULL,
  unit_amount DECIMAL(12,2),
  percentage DECIMAL(6,4),
  tier_config VARCHAR(1000),
  period_type VARCHAR(20),
  description VARCHAR(500),
  is_active BOOLEAN NOT NULL DEFAULT TRUE,
  created_by BIGINT,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_by BIGINT,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_bonus_rules_created_by FOREIGN KEY (created_by) REFERENCES staff(staff_id),
  CONSTRAINT fk_bonus_rules_updated_by FOREIGN KEY (updated_by) REFERENCES staff(staff_id)
);

CREATE TABLE IF NOT EXISTS performance_bonuses (
  bonus_id BIGINT PRIMARY KEY,
  staff_id BIGINT NOT NULL,
  period VARCHAR(50),
  net_count INT,
  bonus_amount DECIMAL(12,2),
  bonus_rule_id BIGINT,
  rule_type VARCHAR(50),
  contract_id BIGINT,
  branch_id BIGINT,
  rent_payment_id BIGINT,
  signed_count INT,
  cancelled_count INT,
  note VARCHAR(500),
  created_by BIGINT,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_performance_bonuses_staff FOREIGN KEY (staff_id) REFERENCES staff(staff_id),
  CONSTRAINT fk_performance_bonuses_rule FOREIGN KEY (bonus_rule_id) REFERENCES bonus_rules(bonus_rule_id),
  CONSTRAINT fk_performance_bonuses_contract FOREIGN KEY (contract_id) REFERENCES contracts(contract_id),
  CONSTRAINT fk_performance_bonuses_branch FOREIGN KEY (branch_id) REFERENCES branches(branch_id),
  CONSTRAINT fk_performance_bonuses_rent_payment FOREIGN KEY (rent_payment_id) REFERENCES rent_payments(rent_payment_id),
  CONSTRAINT fk_performance_bonuses_created_by FOREIGN KEY (created_by) REFERENCES staff(staff_id)
);
