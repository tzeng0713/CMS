CREATE TABLE IF NOT EXISTS role_permissions (
  role_permission_id BIGINT PRIMARY KEY,
  role_name VARCHAR(50) NOT NULL,
  scope VARCHAR(100)
);

CREATE TABLE IF NOT EXISTS branches (
  branch_id BIGINT PRIMARY KEY,
  branch_name VARCHAR(100) NOT NULL
);

CREATE TABLE IF NOT EXISTS offices (
  office_id BIGINT PRIMARY KEY,
  office_no VARCHAR(50),
  branch_id BIGINT NOT NULL,
  phone VARCHAR(80),
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
  phone VARCHAR(100),
  forwarding_address VARCHAR(255),
  petty_cash DECIMAL(12,2),
  referrer VARCHAR(100),
  notes VARCHAR(1000),
  registration_type VARCHAR(60),
  updated_by BIGINT,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_customers_updated_by FOREIGN KEY (updated_by) REFERENCES staff(staff_id)
);

CREATE TABLE IF NOT EXISTS contracts (
  contract_id BIGINT PRIMARY KEY,
  customer_id BIGINT NOT NULL,
  office_id BIGINT,
  rental_item VARCHAR(30),
  rental_status VARCHAR(30),
  signed_date_text VARCHAR(30),
  signer_staff_id BIGINT,
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
  fee_start_month VARCHAR(20),
  fee_end_month VARCHAR(20),
  management_fee DECIMAL(12,2),
  electricity_fee DECIMAL(12,2),
  printing_fee DECIMAL(12,2),
  tax DECIMAL(12,2),
  advance_payment DECIMAL(12,2),
  issued_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_by BIGINT,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_charge_lists_customer FOREIGN KEY (customer_id) REFERENCES customers(customer_id),
  CONSTRAINT fk_charge_lists_contract FOREIGN KEY (contract_id) REFERENCES contracts(contract_id),
  CONSTRAINT fk_charge_lists_updated_by FOREIGN KEY (updated_by) REFERENCES staff(staff_id)
);

CREATE TABLE IF NOT EXISTS refunds (
  refund_id BIGINT PRIMARY KEY,
  customer_id BIGINT,
  contract_id BIGINT,
  company_name VARCHAR(255),
  reason VARCHAR(500),
  refund_amount DECIMAL(12,2),
  note VARCHAR(1000),
  updated_by BIGINT,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_refunds_customer FOREIGN KEY (customer_id) REFERENCES customers(customer_id),
  CONSTRAINT fk_refunds_contract FOREIGN KEY (contract_id) REFERENCES contracts(contract_id),
  CONSTRAINT fk_refunds_updated_by FOREIGN KEY (updated_by) REFERENCES staff(staff_id)
);

CREATE TABLE IF NOT EXISTS sales_targets (
  sales_target_id BIGINT PRIMARY KEY,
  branch_id BIGINT NOT NULL,
  target_month INT,
  category VARCHAR(50),
  target_count INT,
  CONSTRAINT fk_sales_targets_branch FOREIGN KEY (branch_id) REFERENCES branches(branch_id)
);

CREATE TABLE IF NOT EXISTS performance_bonuses (
  bonus_id BIGINT PRIMARY KEY,
  staff_id BIGINT NOT NULL,
  period VARCHAR(50),
  net_count INT,
  bonus_amount DECIMAL(12,2),
  CONSTRAINT fk_performance_bonuses_staff FOREIGN KEY (staff_id) REFERENCES staff(staff_id)
);
