package com.example.cms.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Component
public class SeedDataLoader implements CommandLineRunner {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final boolean enabled;

    public SeedDataLoader(JdbcTemplate jdbc, ObjectMapper objectMapper,
                          @Value("${cms.seed.enabled:true}") boolean enabled) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
    }

    @Override
    public void run(String... args) throws Exception {
        if (!enabled || count("customers") > 0) {
            return;
        }
        ClassPathResource resource = new ClassPathResource("data/seed-data.json");
        if (!resource.exists()) {
            return;
        }
        try (InputStream in = resource.getInputStream()) {
            Map<String, Object> seed = objectMapper.readValue(in, new TypeReference<>() {});
            insertBranches(list(seed, "branches"));
            insertRoles(list(seed, "roles"));
            insertStaff(list(seed, "staff"));
            insertOffices(list(seed, "offices"));
            insertCustomers(list(seed, "customers"));
            insertCustomerRelationGroups(list(seed, "customerRelationGroups"));
            insertCustomerRelationMembers(list(seed, "customerRelationMembers"));
            insertContracts(list(seed, "contracts"));
            insertRentPayments(list(seed, "rentPayments"));
            insertRefunds(list(seed, "refunds"));
            insertSalesTargets(list(seed, "salesTargets"));
            insertPerformanceBonuses(list(seed, "performanceBonuses"));
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> list(Map<String, Object> seed, String key) {
        return (List<Map<String, Object>>) seed.getOrDefault(key, List.of());
    }

    private void insertBranches(List<Map<String, Object>> rows) {
        rows.forEach(r -> jdbc.update(
                "INSERT INTO branches (branch_id, branch_name) VALUES (?, ?)",
                n(r, "branchId"), s(r, "branchName")));
    }

    private void insertRoles(List<Map<String, Object>> rows) {
        rows.forEach(r -> jdbc.update(
                "INSERT INTO role_permissions (role_permission_id, role_name, scope) VALUES (?, ?, ?)",
                n(r, "rolePermissionId"), s(r, "roleName"), s(r, "scope")));
    }

    private void insertStaff(List<Map<String, Object>> rows) {
        rows.forEach(r -> jdbc.update(
                "INSERT INTO staff (staff_id, role_permission_id, branch_id, staff_name, account, password_hash) VALUES (?, ?, ?, ?, ?, ?)",
                n(r, "staffId"), n(r, "rolePermissionId"), n(r, "branchId") == null ? 1L : n(r, "branchId"),
                s(r, "staffName"), s(r, "account"), "{noop}password"));
    }

    private void insertOffices(List<Map<String, Object>> rows) {
        rows.forEach(r -> jdbc.update(
                "INSERT INTO offices (office_id, office_no, branch_id, phone, notes) VALUES (?, ?, ?, ?, ?)",
                n(r, "officeId"), s(r, "officeNo"), n(r, "branchId"), s(r, "phone"), s(r, "notes")));
    }

    private void insertCustomers(List<Map<String, Object>> rows) {
        rows.forEach(r -> jdbc.update("""
                INSERT INTO customers (
                    customer_id, company_name, tax_id, status, rental_item, rental_status,
                    owner_name, owner_birthday, contact_person, contact_birthday, phone, forwarding_address,
                    petty_cash, referrer, accountant_info, account_info, is_agent,
                    notes, registration_type, updated_by
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1)
                """, n(r, "customerId"), s(r, "companyName"), s(r, "taxId"),
                statusCode(s(r, "status")), rentalItem(r), rentalStatus(r), s(r, "ownerName"),
                s(r, "ownerBirthday"), s(r, "contactPerson"), s(r, "contactBirthday"),
                s(r, "phone"), s(r, "forwardingAddress"), bd(r, "pettyCash"),
                s(r, "accountantInfo"), s(r, "accountantInfo"), s(r, "accountInfo"),
                b(r, "isAgent"), s(r, "notes"), rentalItem(r)));
    }

    private void insertCustomerRelationGroups(List<Map<String, Object>> rows) {
        rows.forEach(r -> jdbc.update(
                "INSERT INTO customer_relation_groups (relation_group_id) VALUES (?)",
                n(r, "relationGroupId")));
    }

    private void insertCustomerRelationMembers(List<Map<String, Object>> rows) {
        rows.forEach(r -> jdbc.update("""
                INSERT INTO customer_relation_members (
                    relation_member_id, relation_group_id, customer_id, company_name
                ) VALUES (?, ?, ?, ?)
                """, n(r, "relationMemberId"), n(r, "relationGroupId"),
                n(r, "customerId"), s(r, "companyName")));
    }

    private void insertContracts(List<Map<String, Object>> rows) {
        rows.forEach(r -> jdbc.update("""
                INSERT INTO contracts (
                    contract_id, customer_id, office_id, rental_item, rental_status, signed_date_text,
                    signer_staff_id, partner_staff_id, source_text, payment_months,
                    start_date_text, end_date_text, termination_date_text,
                    rent, deposit, registration_type, lease_status, lease_image_path, updated_by
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1)
                """, n(r, "contractId"), n(r, "customerId"), n(r, "officeId"), contractRentalItem(r),
                contractRentalStatus(r), s(r, "signedDateText"), n(r, "signerStaffId"),
                n(r, "partnerStaffId"), s(r, "sourceText"), n(r, "paymentMonths"),
                s(r, "startDateText"), s(r, "endDateText"), s(r, "terminationDateText"), bd(r, "rent"),
                bd(r, "deposit"), contractRentalItem(r), contractLeaseStatus(r), s(r, "leaseImagePath")));
    }

    private void insertRentPayments(List<Map<String, Object>> rows) {
        rows.forEach(r -> jdbc.update("""
                INSERT INTO rent_payments (
                    rent_payment_id, customer_id, contract_id, payment_month, payment_date_text,
                    fee_start_date_text, fee_end_date_text, amount, receipt_no, note, updated_by
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1)
                """, n(r, "rentPaymentId"), n(r, "customerId"), n(r, "contractId"), n(r, "paymentMonth"),
                s(r, "paymentDateText"), s(r, "feeStartDateText"), s(r, "feeEndDateText"),
                bd(r, "amount"), s(r, "receiptNo"), s(r, "note")));
    }

    private void insertRefunds(List<Map<String, Object>> rows) {
        rows.forEach(r -> jdbc.update("""
                INSERT INTO refunds (refund_id, customer_id, contract_id, company_name, reason, refund_amount, note, updated_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, 1)
                """, n(r, "refundId"), n(r, "customerId"), n(r, "contractId"), s(r, "companyName"),
                s(r, "reason"), bd(r, "refundAmount"), s(r, "note")));
    }

    private void insertSalesTargets(List<Map<String, Object>> rows) {
        rows.forEach(r -> jdbc.update(
                "INSERT INTO sales_targets (sales_target_id, branch_id, target_month, category, target_count) VALUES (?, ?, ?, ?, ?)",
                n(r, "salesTargetId"), n(r, "branchId"), n(r, "month"), s(r, "category"), n(r, "targetCount")));
    }

    private void insertPerformanceBonuses(List<Map<String, Object>> rows) {
        rows.forEach(r -> jdbc.update(
                "INSERT INTO performance_bonuses (bonus_id, staff_id, period, net_count, bonus_amount) VALUES (?, ?, ?, ?, ?)",
                n(r, "bonusId"), n(r, "staffId"), s(r, "period"), n(r, "netCount"), bd(r, "bonusAmount")));
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private String s(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? null : value.toString();
    }

    private Long n(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(value.toString());
    }

    private BigDecimal bd(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value == null) {
            return null;
        }
        return new BigDecimal(value.toString());
    }

    private Boolean b(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value != null && Boolean.parseBoolean(value.toString());
    }

    private Integer statusCode(String value) {
        if (value == null || value.isBlank() || "租賃中".equals(value)) {
            return 0;
        }
        if ("解約中".equals(value) || "停業".equals(value)) {
            return 1;
        }
        if ("合約已到期".equals(value) || "已到期".equals(value)) {
            return 2;
        }
        return Integer.parseInt(value);
    }

    private String rentalItem(Map<String, Object> row) {
        String value = s(row, "rentalItem");
        if (value == null || value.isBlank()) {
            value = s(row, "registrationType");
        }
        if ("實體辦公室".equals(value)) {
            return "辦公室";
        }
        if ("辦公室".equals(value) || "座位".equals(value) || "登記".equals(value)
                || "聯絡處".equals(value) || "停業".equals(value)) {
            return value;
        }
        return "登記";
    }

    private Integer rentalStatus(Map<String, Object> row) {
        Long value = n(row, "rentalStatus");
        if (value != null && value >= 1 && value <= 3) {
            return value.intValue();
        }
        String item = rentalItem(row);
        if ("辦公室".equals(item) || "座位".equals(item)) {
            return 3;
        }
        return 1;
    }

    private String contractRentalItem(Map<String, Object> row) {
        String value = s(row, "rentalItem");
        if (value == null || value.isBlank()) {
            value = s(row, "registrationType");
        }
        if ("實體辦公室".equals(value)) {
            return "辦公室";
        }
        if ("辦公室".equals(value) || "座位".equals(value) || "登記".equals(value)
                || "聯絡處".equals(value) || "停業".equals(value)) {
            return value;
        }
        return "登記";
    }

    private String contractRentalStatus(Map<String, Object> row) {
        String value = s(row, "contractRentalStatus");
        if (value == null || value.isBlank()) {
            value = s(row, "rentalStatusText");
        }
        if ("登記".equals(value) || "辦公室".equals(value)
                || "登記+辦公室".equals(value) || "個人名義".equals(value)) {
            return value;
        }
        String item = contractRentalItem(row);
        return "辦公室".equals(item) ? "辦公室" : "登記";
    }

    private String contractLeaseStatus(Map<String, Object> row) {
        String value = s(row, "leaseStatus");
        if ("已解約".equals(value) || "退租".equals(value)) {
            return "已解約";
        }
        return "綁約中";
    }
}
