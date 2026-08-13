package com.example.cms.service;

import com.example.cms.dto.RentPaymentRequest;
import com.example.cms.service.support.CmsJdbcSupport;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class RentPaymentService extends CmsJdbcSupport {

    public RentPaymentService(JdbcTemplate jdbc) {
        super(jdbc);
    }

    public Map<String, Object> rentPayments(String search, String companyName, String taxId,
                                            String paymentDateStartText, String paymentDateEndText,
                                            Integer page, Integer pageSize) {
        String searchText = search == null ? "" : search.trim();
        String searchLike = "%" + searchText + "%";
        String companyNameText = companyName == null ? "" : companyName.trim();
        String companyNameLike = "%" + companyNameText + "%";
        String taxIdText = taxId == null ? "" : taxId.trim();
        String taxIdLike = "%" + taxIdText + "%";
        String paymentDateStart = paymentDateStartText == null ? "" : paymentDateStartText.trim();
        String paymentDateEnd = paymentDateEndText == null ? "" : paymentDateEndText.trim();
        String where = """
                WHERE (? = '%%'
                   OR c.company_name LIKE ?
                   OR c.owner_name LIKE ?
                  OR c.tax_id LIKE ?
                  OR rp.receipt_no LIKE ?)
                  AND (? = '' OR c.company_name LIKE ?)
                  AND (? = '' OR c.tax_id LIKE ?)
                  AND (? = '' OR rp.payment_date_text >= ?)
                  AND (? = '' OR rp.payment_date_text <= ?)
                """;
        Object[] filterArguments = {
                searchLike, searchLike, searchLike, searchLike, searchLike,
                companyNameText, companyNameLike, taxIdText, taxIdLike,
                paymentDateStart, paymentDateStart, paymentDateEnd, paymentDateEnd
        };
        int size = pageSize == null || pageSize <= 0 ? 20 : Math.min(pageSize, 200);
        int pageNumber = page == null || page < 0 ? 0 : page;
        var pageArguments = new ArrayList<>(java.util.List.of(filterArguments));
        pageArguments.add(size);
        pageArguments.add(pageNumber * size);
        var rows = jdbc.queryForList("""
                SELECT rp.*, c.company_name, c.owner_name, c.tax_id
                FROM rent_payments rp
                JOIN customers c ON c.customer_id = rp.customer_id
                """ + where + " ORDER BY rp.rent_payment_id DESC LIMIT ? OFFSET ?", pageArguments.toArray());
        Long total = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM rent_payments rp
                JOIN customers c ON c.customer_id = rp.customer_id
                """ + where, Long.class, filterArguments);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("content", rows);
        result.put("totalElements", total == null ? 0L : total);
        result.put("page", pageNumber);
        result.put("pageSize", size);
        return result;
    }

    public Map<String, Object> createRentPayment(RentPaymentRequest request) {
        Long id = nextId("rent_payments", "rent_payment_id");
        jdbc.update("""
                INSERT INTO rent_payments (
                    rent_payment_id, customer_id, contract_id, payment_month, payment_date_text,
                    fee_start_date_text, fee_end_date_text, amount, receipt_no, note, updated_by
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                id,
                request.customerId(),
                request.contractId(),
                request.paymentMonth(),
                request.paymentDateText(),
                request.feeStartDateText(),
                request.feeEndDateText(),
                request.amount(),
                request.receiptNo(),
                request.note(),
                request.updatedBy() == null ? 1L : request.updatedBy());
        return jdbc.queryForMap("SELECT * FROM rent_payments WHERE rent_payment_id = ?", id);
    }

    public Map<String, Object> updateRentPayment(long id, RentPaymentRequest request) {
        jdbc.update("""
                UPDATE rent_payments
                SET payment_month = ?,
                    payment_date_text = ?,
                    fee_start_date_text = ?,
                    fee_end_date_text = ?,
                    amount = ?,
                    receipt_no = ?,
                    note = ?,
                    updated_by = ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE rent_payment_id = ?
                """,
                request.paymentMonth(),
                request.paymentDateText(),
                request.feeStartDateText(),
                request.feeEndDateText(),
                request.amount(),
                request.receiptNo(),
                request.note(),
                request.updatedBy() == null ? 1L : request.updatedBy(),
                id);
        return jdbc.queryForMap("""
                SELECT rp.*, c.company_name, c.owner_name, c.tax_id
                FROM rent_payments rp
                JOIN customers c ON c.customer_id = rp.customer_id
                WHERE rp.rent_payment_id = ?
                """, id);
    }
}
