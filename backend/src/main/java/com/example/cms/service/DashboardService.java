package com.example.cms.service;

import com.example.cms.service.support.CmsJdbcSupport;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DashboardService extends CmsJdbcSupport {

    public DashboardService(JdbcTemplate jdbc) {
        super(jdbc);
    }

    public Map<String, Object> dashboard() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("customers", count("customers"));
        result.put("activeContracts", countWhere("contracts", "lease_status = '綁約中'"));
        result.put("officeCustomers", latestActiveContractCount("辦公室", "登記+辦公室"));
        result.put("registrationCustomers", latestActiveContractCount("登記", "登記+辦公室"));
        result.put("rentPayments", count("rent_payments"));
        result.put("refunds", count("refunds"));
        result.put("monthlyRentAmount", jdbc.queryForObject(
                "SELECT COALESCE(SUM(amount), 0) FROM rent_payments", Number.class));
        result.put("latestPayments", jdbc.queryForList("""
                SELECT rp.rent_payment_id, c.company_name, rp.payment_month, rp.amount, rp.receipt_no
                FROM rent_payments rp
                JOIN customers c ON c.customer_id = rp.customer_id
                ORDER BY rp.rent_payment_id DESC
                LIMIT 8
                """));
        result.put("notifications", dashboardNotifications());
        return result;
    }

    private Map<String, Object> dashboardNotifications() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Taipei"));
        Map<String, Object> notifications = new LinkedHashMap<>();
        notifications.put("expiringContracts", expiringContractNotifications(today));
        notifications.put("unpaidRent", unpaidRentNotifications(today));
        notifications.put("incompleteContracts", incompleteContractNotifications());
        return notifications;
    }

    private List<Map<String, Object>> expiringContractNotifications(LocalDate today) {
        return jdbc.queryForList("""
                SELECT c.company_name, co.end_date_text, co.rental_item
                FROM contracts co
                JOIN customers c ON c.customer_id = co.customer_id
                WHERE co.lease_status = '綁約中'
                  AND co.contract_id = (
                    SELECT MAX(latest.contract_id) FROM contracts latest
                    WHERE latest.customer_id = co.customer_id
                  )
                  AND co.end_date_text IS NOT NULL
                  AND co.end_date_text <> ''
                ORDER BY co.end_date_text, c.company_name
                """).stream()
                .filter(row -> shouldShowExpirationReminder((String) row.get("end_date_text"), today))
                .sorted((left, right) -> localDate((String) left.get("end_date_text"))
                        .compareTo(localDate((String) right.get("end_date_text"))))
                .toList();
    }

    private List<Map<String, Object>> unpaidRentNotifications(LocalDate today) {
        List<Map<String, Object>> contracts = jdbc.queryForList("""
                SELECT c.customer_id, c.company_name, co.contract_id, co.payment_months,
                       co.rent, co.end_date_text
                FROM contracts co
                JOIN customers c ON c.customer_id = co.customer_id
                WHERE co.lease_status = '綁約中'
                  AND co.contract_id = (
                    SELECT MAX(latest.contract_id) FROM contracts latest
                    WHERE latest.customer_id = co.customer_id
                  )
                  AND EXISTS (
                    SELECT 1 FROM rent_payments rp WHERE rp.contract_id = co.contract_id
                  )
                ORDER BY c.company_name
                """);
        List<Map<String, Object>> reminders = new ArrayList<>();
        for (Map<String, Object> contract : contracts) {
            long contractId = ((Number) contract.get("contract_id")).longValue();
            LocalDate latestCoveredDate = jdbc.queryForList("""
                            SELECT fee_end_date_text FROM rent_payments
                            WHERE contract_id = ?
                              AND fee_end_date_text IS NOT NULL
                              AND fee_end_date_text <> ''
                            """, contractId).stream()
                    .map(row -> localDate((String) row.get("fee_end_date_text")))
                    .filter(java.util.Objects::nonNull)
                    .max(LocalDate::compareTo)
                    .orElse(null);
            LocalDate contractEnd = localDate((String) contract.get("end_date_text"));
            if (latestCoveredDate == null || contractEnd == null) {
                continue;
            }
            LocalDate nextPeriodStart = latestCoveredDate.plusDays(1);
            if (nextPeriodStart.isAfter(contractEnd)
                    || today.isBefore(nextPeriodStart.minusDays(30))) {
                continue;
            }
            Map<String, Object> reminder = new LinkedHashMap<>(contract);
            reminder.put("next_period_start", nextPeriodStart.toString());
            Number rent = (Number) contract.get("rent");
            Number paymentMonths = (Number) contract.get("payment_months");
            reminder.put("suggested_amount", rent == null || paymentMonths == null
                    ? null
                    : new BigDecimal(rent.toString()).multiply(BigDecimal.valueOf(paymentMonths.longValue())));
            reminders.add(reminder);
        }
        return reminders.stream()
                .sorted((left, right) -> ((String) left.get("next_period_start"))
                        .compareTo((String) right.get("next_period_start")))
                .toList();
    }

    private List<Map<String, Object>> incompleteContractNotifications() {
        return jdbc.queryForList("""
                SELECT c.customer_id, c.company_name, co.contract_id, co.signed_date_text,
                       co.start_date_text, co.rent, co.lease_status,
                       co.signer_staff_id, s.staff_name AS signer_staff_name
                FROM contracts co
                JOIN customers c ON c.customer_id = co.customer_id
                LEFT JOIN staff s ON s.staff_id = co.signer_staff_id
                WHERE co.lease_status = '綁約中'
                  AND co.contract_id = (
                    SELECT MAX(latest.contract_id) FROM contracts latest
                    WHERE latest.customer_id = co.customer_id
                  )
                  AND NOT EXISTS (
                    SELECT 1 FROM rent_payments rp WHERE rp.contract_id = co.contract_id
                )
                ORDER BY co.signed_date_text, co.contract_id DESC
                """);
    }

    private boolean shouldShowExpirationReminder(String endDateText, LocalDate today) {
        LocalDate endDate = localDate(endDateText);
        if (endDate == null) {
            return false;
        }
        LocalDate reminderStart = endDate.minusDays(45);
        return !today.isBefore(reminderStart) && !today.isAfter(endDate);
    }

    private int latestActiveContractCount(String firstRentalStatus, String secondRentalStatus) {
        return jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM contracts co
                WHERE co.contract_id = (
                    SELECT MAX(latest.contract_id) FROM contracts latest
                    WHERE latest.customer_id = co.customer_id
                )
                  AND co.lease_status = '綁約中'
                  AND co.rental_status IN (?, ?)
                """, Integer.class, firstRentalStatus, secondRentalStatus);
    }
}
