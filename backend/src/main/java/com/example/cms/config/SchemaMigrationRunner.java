package com.example.cms.config;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Types;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SchemaMigrationRunner implements org.springframework.boot.CommandLineRunner {
    private final JdbcTemplate jdbc;

    public SchemaMigrationRunner(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(String... args) {
        migrateRoleNames();
        migrateStaffBranch();
        migrateCustomerRentalFields();
        migrateCustomerWorkflowFields();
        migrateCustomerRelationTables();
        migrateCustomerStatusToCode();
        migrateContractRentalFields();
        migrateContractWorkflowFields();
        migrateContractPaymentMonths();
        migrateContractOfficeNullable();
        migrateContractLeaseStatus();
        migrateRefundsTable();
        migrateChargeListsTable();
        migrateBranchFields();
    }

    private void migrateRoleNames() {
        jdbc.update("UPDATE role_permissions SET role_name = '督導秘書' WHERE role_name = '管理人員'");
        jdbc.update("UPDATE role_permissions SET role_name = '一般秘書' WHERE role_name = '一般人員'");
        jdbc.update("UPDATE staff SET staff_name = '督導秘書' WHERE staff_name = '管理人員'");
        jdbc.update("UPDATE staff SET staff_name = '一般秘書' WHERE staff_name = '一般人員'");
    }

    private void migrateStaffBranch() {
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            try (var columns = connection.getMetaData().getColumns(null, null, "staff", "branch_id")) {
                if (columns.next()) {
                    return null;
                }
            }
            jdbc.execute("ALTER TABLE staff ADD COLUMN branch_id BIGINT DEFAULT 1");
            jdbc.update("UPDATE staff SET branch_id = 1 WHERE branch_id IS NULL");
            return null;
        });
    }

    private void migrateCustomerRentalFields() {
        addColumnIfMissing("customers", "rental_item", "VARCHAR(30)");
        addColumnIfMissing("customers", "rental_status", "TINYINT DEFAULT 1");
        addColumnIfMissing("customers", "owner_birthday", "VARCHAR(30)");
        addColumnIfMissing("customers", "referrer", "VARCHAR(100)");
        jdbc.update("""
                UPDATE customers
                SET rental_item = CASE
                    WHEN registration_type IN ('實體辦公室', '辦公室') THEN '辦公室'
                    WHEN registration_type IN ('座位') THEN '座位'
                    WHEN registration_type IN ('聯絡處') THEN '聯絡處'
                    ELSE '登記'
                END
                WHERE rental_item IS NULL OR rental_item = ''
                """);
        jdbc.update("""
                UPDATE customers
                SET rental_status = CASE
                    WHEN rental_item IN ('登記', '聯絡處') THEN 1
                    WHEN rental_item IN ('辦公室', '座位') THEN 3
                    ELSE 1
                END
                WHERE rental_status IS NULL OR rental_status NOT IN (1, 2, 3)
                """);
    }

    private void migrateCustomerWorkflowFields() {
        addColumnIfMissing("customers", "contact_birthday", "VARCHAR(30)");
        addColumnIfMissing("customers", "accountant_info", "VARCHAR(500)");
        addColumnIfMissing("customers", "account_info", "VARCHAR(1000)");
        addColumnIfMissing("customers", "is_agent", "BOOLEAN DEFAULT FALSE");
        jdbc.update("""
                UPDATE customers
                SET accountant_info = referrer
                WHERE (accountant_info IS NULL OR accountant_info = '')
                  AND referrer IS NOT NULL
                  AND referrer <> ''
                """);
    }

    private void migrateCustomerRelationTables() {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS customer_relation_groups (
                  relation_group_id BIGINT PRIMARY KEY,
                  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS customer_relation_members (
                  relation_member_id BIGINT PRIMARY KEY,
                  relation_group_id BIGINT NOT NULL,
                  customer_id BIGINT,
                  company_name VARCHAR(255) NOT NULL,
                  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                  CONSTRAINT uq_relation_member_customer UNIQUE (customer_id),
                  CONSTRAINT uq_relation_member_name UNIQUE (relation_group_id, company_name),
                  CONSTRAINT fk_relation_members_group FOREIGN KEY (relation_group_id)
                    REFERENCES customer_relation_groups(relation_group_id),
                  CONSTRAINT fk_relation_members_customer FOREIGN KEY (customer_id)
                    REFERENCES customers(customer_id)
                )
                """);
    }

    private void addColumnIfMissing(String tableName, String columnName, String definition) {
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            try (var columns = connection.getMetaData().getColumns(null, null, tableName, columnName)) {
                if (columns.next()) {
                    return null;
                }
            }
            jdbc.execute("ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + definition);
            return null;
        });
    }

    private void migrateCustomerStatusToCode() {
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            try (var columns = connection.getMetaData().getColumns(null, null, "customers", "status")) {
                if (!columns.next()) {
                    return null;
                }
                int type = columns.getInt("DATA_TYPE");
                if (type == Types.INTEGER || type == Types.TINYINT || type == Types.SMALLINT) {
                    return null;
                }
            }

            jdbc.update("""
                    UPDATE customers
                    SET status = CASE
                        WHEN status IN ('0', '1', '2') THEN status
                        WHEN status = '租賃中' THEN '0'
                        WHEN status IN ('解約中', '停業') THEN '1'
                        WHEN status IN ('合約已到期', '已到期') THEN '2'
                        ELSE '0'
                    END
                    """);
            jdbc.execute("ALTER TABLE customers MODIFY status TINYINT DEFAULT 0");
            return null;
        });
    }

    private void migrateContractPaymentMonths() {
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            try (var columns = connection.getMetaData().getColumns(null, null, "contracts", "payment_months")) {
                if (columns.next()) {
                    return null;
                }
            }
            jdbc.execute("ALTER TABLE contracts ADD COLUMN payment_months INT");
            return null;
        });
    }

    private void migrateContractRentalFields() {
        addColumnIfMissing("contracts", "rental_item", "VARCHAR(30)");
        addColumnIfMissing("contracts", "rental_status", "VARCHAR(30)");
        addColumnIfMissing("contracts", "signed_date_text", "VARCHAR(30)");
        addColumnIfMissing("contracts", "signer_staff_id", "BIGINT");
        addColumnIfMissing("contracts", "lease_image_path", "VARCHAR(500)");
        jdbc.update("""
                UPDATE contracts
                SET rental_item = CASE
                    WHEN registration_type IN ('實體辦公室', '辦公室') THEN '辦公室'
                    WHEN registration_type IN ('座位') THEN '座位'
                    WHEN registration_type IN ('聯絡處') THEN '聯絡處'
                    ELSE '登記'
                END
                WHERE rental_item IS NULL OR rental_item = ''
                """);
        jdbc.update("""
                UPDATE contracts
                SET rental_status = CASE
                    WHEN rental_item = '辦公室' THEN '辦公室'
                    WHEN rental_item IN ('登記', '聯絡處') THEN '登記'
                    ELSE rental_item
                END
                WHERE rental_status IS NULL OR rental_status = ''
                """);
    }

    private void migrateContractWorkflowFields() {
        addColumnIfMissing("contracts", "partner_staff_id", "BIGINT");
        addColumnIfMissing("contracts", "source_text", "VARCHAR(500)");
    }

    private void migrateContractOfficeNullable() {
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            String database = connection.getMetaData().getDatabaseProductName().toLowerCase();
            if (database.contains("mysql")) {
                jdbc.execute("ALTER TABLE contracts MODIFY office_id BIGINT NULL");
            }
            return null;
        });
    }

    private void migrateRefundsTable() {
        // rename note → adjustment_note, reason → refund_reason (MySQL only; H2 gets clean schema from schema.sql)
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            String db = connection.getMetaData().getDatabaseProductName().toLowerCase();
            if (!db.contains("mysql")) return null;
            boolean hasNote = false, hasAdjNote = false;
            try (var cols = connection.getMetaData().getColumns(null, null, "refunds", "note")) {
                hasNote = cols.next();
            }
            try (var cols = connection.getMetaData().getColumns(null, null, "refunds", "adjustment_note")) {
                hasAdjNote = cols.next();
            }
            if (hasNote && !hasAdjNote) {
                jdbc.execute("ALTER TABLE refunds CHANGE note adjustment_note VARCHAR(1000)");
            }
            boolean hasReason = false, hasRefundReason = false;
            try (var cols = connection.getMetaData().getColumns(null, null, "refunds", "reason")) {
                hasReason = cols.next();
            }
            try (var cols = connection.getMetaData().getColumns(null, null, "refunds", "refund_reason")) {
                hasRefundReason = cols.next();
            }
            if (hasReason && !hasRefundReason) {
                jdbc.execute("ALTER TABLE refunds CHANGE reason refund_reason VARCHAR(500)");
            }
            return null;
        });
        addColumnIfMissing("refunds", "charge_list_id",        "BIGINT");
        addColumnIfMissing("refunds", "adjustment_amount",     "DECIMAL(12,2) DEFAULT 0");
        addColumnIfMissing("refunds", "adjustment_note",       "VARCHAR(1000)");
        addColumnIfMissing("refunds", "deduction_total",       "DECIMAL(12,2)");
        addColumnIfMissing("refunds", "refund_status",         "VARCHAR(20)");
        addColumnIfMissing("refunds", "payment_method",        "VARCHAR(20)");
        addColumnIfMissing("refunds", "bank_code",             "VARCHAR(20)");
        addColumnIfMissing("refunds", "bank_account",          "VARCHAR(50)");
        addColumnIfMissing("refunds", "bank_account_name",     "VARCHAR(100)");
        addColumnIfMissing("refunds", "refunded_at",           "VARCHAR(30)");
        addColumnIfMissing("refunds", "termination_staff_id",  "BIGINT");
        addColumnIfMissing("refunds", "created_by",            "BIGINT");
        addColumnIfMissing("refunds", "created_at",            "TIMESTAMP DEFAULT CURRENT_TIMESTAMP");
        addColumnIfMissing("refunds", "reviewed_by",           "BIGINT");
        addColumnIfMissing("refunds", "reviewed_at",           "TIMESTAMP");

        addForeignKeyIfMissing("refunds", "fk_refunds_charge_list", "charge_list_id", "charge_lists", "charge_list_id");
        addForeignKeyIfMissing("refunds", "fk_refunds_termination_staff", "termination_staff_id", "staff", "staff_id");
        addForeignKeyIfMissing("refunds", "fk_refunds_created_by", "created_by", "staff", "staff_id");
        addForeignKeyIfMissing("refunds", "fk_refunds_reviewed_by", "reviewed_by", "staff", "staff_id");
        addIndexIfMissing("refunds", "idx_refunds_status", "refund_status");
        addIndexIfMissing("refunds", "idx_refunds_refunded_at", "refunded_at");

        jdbc.update("""
                UPDATE refunds
                SET refund_status = CASE WHEN refunded_at IS NOT NULL THEN '已退款' ELSE '草稿' END
                WHERE refund_status IS NULL OR refund_status = ''
                """);
    }


    private void migrateChargeListsTable() {
        migrateChargeListsFeeMonth();
        addColumnIfMissing("charge_lists", "repair_fee", "DECIMAL(12,2) DEFAULT 0");
        addColumnIfMissing("charge_lists", "meeting_room_fee", "DECIMAL(12,2) DEFAULT 0");
        addColumnIfMissing("charge_lists", "total_amount", "DECIMAL(12,2) DEFAULT 0");
        addColumnIfMissing("charge_lists", "status", "TINYINT DEFAULT 2");
        addColumnIfMissing("charge_lists", "created_by", "BIGINT");

        jdbc.update("""
                UPDATE charge_lists
                SET management_fee   = COALESCE(management_fee, 0),
                    electricity_fee  = COALESCE(electricity_fee, 0),
                    printing_fee     = COALESCE(printing_fee, 0),
                    meeting_room_fee = COALESCE(meeting_room_fee, 0),
                    tax              = COALESCE(tax, 0),
                    advance_payment  = COALESCE(advance_payment, 0),
                    repair_fee       = COALESCE(repair_fee, 0),
                    status           = COALESCE(status, 2),
                    created_by       = COALESCE(created_by, updated_by)
                """);
        jdbc.update("""
                UPDATE charge_lists
                SET total_amount = management_fee + electricity_fee + printing_fee + meeting_room_fee
                    + tax + advance_payment + repair_fee
                WHERE total_amount IS NULL OR total_amount = 0
                """);

        jdbc.execute((ConnectionCallback<Void>) connection -> {
            String db = connection.getMetaData().getDatabaseProductName().toLowerCase();
            if (!db.contains("mysql")) return null;

            jdbc.execute("ALTER TABLE charge_lists MODIFY fee_month CHAR(7)");
            jdbc.execute("ALTER TABLE charge_lists MODIFY management_fee DECIMAL(12,2) NOT NULL DEFAULT 0");
            jdbc.execute("ALTER TABLE charge_lists MODIFY electricity_fee DECIMAL(12,2) NOT NULL DEFAULT 0");
            jdbc.execute("ALTER TABLE charge_lists MODIFY printing_fee DECIMAL(12,2) NOT NULL DEFAULT 0");
            jdbc.execute("ALTER TABLE charge_lists MODIFY meeting_room_fee DECIMAL(12,2) NOT NULL DEFAULT 0");
            jdbc.execute("ALTER TABLE charge_lists MODIFY tax DECIMAL(12,2) NOT NULL DEFAULT 0");
            jdbc.execute("ALTER TABLE charge_lists MODIFY advance_payment DECIMAL(12,2) NOT NULL DEFAULT 0");
            jdbc.execute("ALTER TABLE charge_lists MODIFY repair_fee DECIMAL(12,2) NOT NULL DEFAULT 0");
            jdbc.execute("ALTER TABLE charge_lists MODIFY total_amount DECIMAL(12,2) NOT NULL DEFAULT 0");
            jdbc.execute("ALTER TABLE charge_lists MODIFY updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP");
            return null;
        });

        addForeignKeyIfMissing("charge_lists", "fk_charge_lists_created_by", "created_by", "staff", "staff_id");

        addIndexIfMissing("charge_lists", "idx_charge_lists_customer_id", "customer_id");
        addIndexIfMissing("charge_lists", "idx_charge_lists_contract_id", "contract_id");
        addIndexIfMissing("charge_lists", "idx_charge_lists_status", "status");
        addIndexIfMissing("charge_lists", "idx_charge_lists_issued_at", "issued_at");
        addIndexIfMissing("charge_lists", "idx_charge_lists_fee_month", "fee_month");
    }

    // Charge lists used to bill a fee-month range (fee_start_month/fee_end_month); the
    // feature was simplified to bill a single month, so on MySQL we fold the range down
    // to fee_month by reusing the old start month and dropping the end month column.
    // Dev/test data is not preserved beyond that (confirmed acceptable).
    private void migrateChargeListsFeeMonth() {
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            boolean hasFeeMonth;
            try (var cols = connection.getMetaData().getColumns(null, null, "charge_lists", "fee_month")) {
                hasFeeMonth = cols.next();
            }
            if (hasFeeMonth) {
                return null;
            }
            String db = connection.getMetaData().getDatabaseProductName().toLowerCase();
            boolean hasFeeStartMonth;
            try (var cols = connection.getMetaData().getColumns(null, null, "charge_lists", "fee_start_month")) {
                hasFeeStartMonth = cols.next();
            }
            if (db.contains("mysql") && hasFeeStartMonth) {
                jdbc.execute("ALTER TABLE charge_lists CHANGE COLUMN fee_start_month fee_month CHAR(7)");
                jdbc.execute("ALTER TABLE charge_lists DROP COLUMN fee_end_month");
            } else {
                jdbc.execute("ALTER TABLE charge_lists ADD COLUMN fee_month CHAR(7)");
            }
            return null;
        });
    }

    private void addForeignKeyIfMissing(String table, String fkName, String column, String refTable, String refColumn) {
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            try (var keys = connection.getMetaData().getImportedKeys(null, null, table)) {
                while (keys.next()) {
                    if (fkName.equalsIgnoreCase(keys.getString("FK_NAME"))) {
                        return null;
                    }
                }
            }
            jdbc.execute("ALTER TABLE " + table + " ADD CONSTRAINT " + fkName
                    + " FOREIGN KEY (" + column + ") REFERENCES " + refTable + "(" + refColumn + ")");
            return null;
        });
    }

    private void addIndexIfMissing(String table, String indexName, String column) {
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            try (var indexes = connection.getMetaData().getIndexInfo(null, null, table, false, false)) {
                while (indexes.next()) {
                    if (indexName.equalsIgnoreCase(indexes.getString("INDEX_NAME"))) {
                        return null;
                    }
                }
            }
            jdbc.execute("CREATE INDEX " + indexName + " ON " + table + "(" + column + ")");
            return null;
        });
    }

    private void migrateBranchFields() {
        addColumnIfMissing("branches", "company_name",      "VARCHAR(100)");
        addColumnIfMissing("branches", "branch_code",       "VARCHAR(50)");
        addColumnIfMissing("branches", "branch_address",    "VARCHAR(255)");
        addColumnIfMissing("branches", "tax_id",            "VARCHAR(30)");
        addColumnIfMissing("branches", "bank_account",      "VARCHAR(50)");
        addColumnIfMissing("branches", "bank_branch",       "VARCHAR(100)");
        addColumnIfMissing("branches", "bank_account_name", "VARCHAR(100)");
    }

    private void migrateContractLeaseStatus() {
        jdbc.update("""
                UPDATE contracts
                SET lease_status = CASE
                    WHEN lease_status IN ('已解約', '退租', '已退租', '解約中', '已停業', '停業') THEN '已解約'
                    ELSE '綁約中'
                END
                WHERE lease_status IS NULL
                   OR lease_status NOT IN ('綁約中', '已解約')
                """);
    }
}
