package com.example.cms.service;

import com.example.cms.dto.RentPaymentRequest;
import com.example.cms.service.support.CmsJdbcSupport;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class RentPaymentService extends CmsJdbcSupport {

    public RentPaymentService(JdbcTemplate jdbc) {
        super(jdbc);
    }

    public List<Map<String, Object>> rentPayments(String search) {
        String searchText = search == null ? "" : search.trim();
        String like = "%" + searchText + "%";
        return jdbc.queryForList("""
                SELECT rp.*, c.company_name, c.owner_name, c.tax_id
                FROM rent_payments rp
                JOIN customers c ON c.customer_id = rp.customer_id
                WHERE (? = '%%'
                   OR c.company_name LIKE ?
                   OR c.owner_name LIKE ?
                   OR c.tax_id LIKE ?
                   OR rp.receipt_no LIKE ?)
                ORDER BY rp.rent_payment_id DESC
                LIMIT 300
                """, like, like, like, like, like);
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
