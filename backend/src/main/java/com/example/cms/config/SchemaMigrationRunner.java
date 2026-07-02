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
        migrateCustomerStatusToCode();
        migrateContractRentalFields();
        migrateContractPaymentMonths();
        migrateContractOfficeNullable();
        migrateContractLeaseStatus();
        migrateRefundsTable();
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
        // rename note → adjustment_note (MySQL only; H2 gets clean schema from schema.sql)
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
