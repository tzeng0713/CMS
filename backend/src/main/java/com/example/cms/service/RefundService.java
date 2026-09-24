package com.example.cms.service;

import com.example.cms.dto.ImportChargeListRequest;
import com.example.cms.dto.RefundCancelRequest;
import com.example.cms.dto.RefundRequest;
import com.example.cms.dto.RefundReviewRequest;
import com.example.cms.exception.RefundOverDeductedException;
import com.example.cms.service.support.CmsJdbcSupport;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class RefundService extends CmsJdbcSupport {

    private static final Map<String, String> SORT_COLUMNS = Map.of(
            "refundId", "r.refund_id",
            "customerId", "r.customer_id",
            "refundAmount", "r.refund_amount",
            "refundStatus", "r.refund_status",
            "createdAt", "r.created_at"
    );

    public RefundService(JdbcTemplate jdbc) {
        super(jdbc);
    }

    public Map<String, Object> refunds(String companyName, String taxId, String dateFrom, String dateTo,
                                        String status, Integer page, Integer pageSize, String sortBy, String sortDir) {
        String companyNameText = blankToNull(companyName);
        String taxIdText = blankToNull(taxId);
        String dateFromText = blankToNull(dateFrom);
        String dateToText = blankToNull(dateTo);
        String statusText = blankToNull(status);

        String where = """
                 WHERE (? IS NULL OR c.company_name LIKE CONCAT('%', ?, '%') OR r.company_name LIKE CONCAT('%', ?, '%'))
                   AND (? IS NULL OR c.tax_id = ?)
                   AND (? IS NULL OR DATE(r.created_at) >= ?)
                   AND (? IS NULL OR DATE(r.created_at) <= ?)
                   AND (? IS NULL OR r.refund_status = ?)
                """;
        List<Object> args = new ArrayList<>();
        args.add(companyNameText); args.add(companyNameText); args.add(companyNameText);
        args.add(taxIdText); args.add(taxIdText);
        args.add(dateFromText); args.add(dateFromText);
        args.add(dateToText); args.add(dateToText);
        args.add(statusText); args.add(statusText);

        String sortColumn = sortBy == null ? "r.created_at" : SORT_COLUMNS.getOrDefault(sortBy, "r.created_at");
        String direction = "asc".equalsIgnoreCase(sortDir) ? "ASC" : "DESC";
        int size = pageSize == null || pageSize <= 0 ? 20 : Math.min(pageSize, 200);
        int pageNumber = page == null || page < 0 ? 0 : page;

        List<Object> selectArgs = new ArrayList<>(args);
        selectArgs.add(size);
        selectArgs.add(pageNumber * size);
        List<Map<String, Object>> rows = jdbc.queryForList(
                refundListSql() + where + " ORDER BY " + sortColumn + " " + direction + " LIMIT ? OFFSET ?",
                selectArgs.toArray());

        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM refunds r LEFT JOIN customers c ON c.customer_id = r.customer_id" + where,
                Long.class, args.toArray());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("content", rows);
        result.put("totalElements", total);
        result.put("page", pageNumber);
        result.put("pageSize", size);
        return result;
    }

    public Map<String, Object> refundDetail(long id) {
        try {
            return jdbc.queryForMap(refundListSql() + " WHERE r.refund_id = ?", id);
        } catch (EmptyResultDataAccessException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "refund not found");
        }
    }

    public Map<String, Object> createRefund(RefundRequest request) {
        requireExistingCustomer(request.customerId());
        requireExistingContract(request.contractId());
        requireContractBelongsToCustomer(request.contractId(), request.customerId());
        requirePaymentInfo(request);
        BigDecimal deductionTotal = zeroIfNull(request.deductionTotal());
        requireNonNegative(deductionTotal, "deductionTotal");
        BigDecimal adjustmentAmount = zeroIfNull(request.adjustmentAmount());

        boolean midTermTermination = Boolean.TRUE.equals(request.midTermTermination());
        RefundBase refundBase = refundBaseAmount(request.contractId(), midTermTermination);
        BigDecimal refundAmount = refundBase.amount().add(adjustmentAmount).subtract(deductionTotal);
        requireNotOverDeducted(refundAmount, request.chargeListId(), request.customerId(), request.contractId());

        String companyName = jdbc.queryForObject(
                "SELECT company_name FROM customers WHERE customer_id = ?", String.class, request.customerId());

        String status = "待審核".equals(blankToNull(request.refundStatus())) ? "待審核" : "草稿";
        Long id = nextId("refunds", "refund_id");
        Long staffId = request.staffId() == null ? 1L : request.staffId();

        jdbc.update("""
                INSERT INTO refunds (
                    refund_id, customer_id, contract_id, charge_list_id, company_name, refund_reason,
                    adjustment_amount, adjustment_note, deduction_total, refund_amount, mid_term_termination, refund_status,
                    payment_method, bank_code, bank_account, bank_account_name,
                    created_by, created_at, updated_by, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, ?, CURRENT_TIMESTAMP)
                """,
                id, request.customerId(), request.contractId(), request.chargeListId(), companyName,
                blankToNull(request.refundReason()), adjustmentAmount, blankToNull(request.adjustmentNote()),
                deductionTotal, refundAmount, midTermTermination, status,
                blankToNull(request.paymentMethod()), blankToNull(request.bankCode()),
                blankToNull(request.bankAccount()), blankToNull(request.bankAccountName()),
                staffId, staffId);

        Map<String, Object> result = refundDetail(id);
        String message = refundMessage(refundBase);
        if (message != null) {
            result.put("message", message);
        }
        return result;
    }

    public Map<String, Object> updateRefund(long id, RefundRequest request) {
        Map<String, Object> current = refundDetail(id);
        String currentStatus = (String) current.get("refund_status");
        Long staffId = request.staffId() == null ? 1L : request.staffId();

        if ("已退款".equals(currentStatus) || "已取消".equals(currentStatus)) {
            throw new IllegalArgumentException("已退款或已取消的退款單無法修改");
        }
        requirePaymentInfo(request);

        if ("審核通過".equals(currentStatus)) {
            if (!"已退款".equals(blankToNull(request.refundStatus()))) {
                throw new IllegalArgumentException("審核通過的退款單僅能辦理退款，不可修改金額");
            }
            requireNonBlank(request.refundedAt(), "refundedAt");
            jdbc.update("""
                    UPDATE refunds
                    SET refund_status = '已退款',
                        payment_method = ?, bank_code = ?, bank_account = ?, bank_account_name = ?,
                        refunded_at = ?, updated_by = ?, updated_at = CURRENT_TIMESTAMP
                    WHERE refund_id = ?
                    """,
                    blankToNull(request.paymentMethod()), blankToNull(request.bankCode()),
                    blankToNull(request.bankAccount()), blankToNull(request.bankAccountName()),
                    blankToNull(request.refundedAt()), staffId, id);
            if (current.get("charge_list_id") != null) {
                markChargeListSettled(((Number) current.get("charge_list_id")).longValue(), staffId);
            }
            return refundDetail(id);
        }

        requireExistingCustomer(request.customerId());
        requireExistingContract(request.contractId());
        requireContractBelongsToCustomer(request.contractId(), request.customerId());
        BigDecimal deductionTotal = zeroIfNull(request.deductionTotal());
        requireNonNegative(deductionTotal, "deductionTotal");
        BigDecimal adjustmentAmount = zeroIfNull(request.adjustmentAmount());

        boolean midTermTermination = Boolean.TRUE.equals(request.midTermTermination());
        RefundBase refundBase = refundBaseAmount(request.contractId(), midTermTermination);
        BigDecimal refundAmount = refundBase.amount().add(adjustmentAmount).subtract(deductionTotal);
        requireNotOverDeducted(refundAmount, request.chargeListId(), request.customerId(), request.contractId());

        String nextStatus = blankToNull(request.refundStatus());
        if (!"草稿".equals(nextStatus) && !"待審核".equals(nextStatus)) {
            nextStatus = currentStatus;
        }

        jdbc.update("""
                UPDATE refunds
                SET customer_id = ?, contract_id = ?, charge_list_id = ?, refund_reason = ?,
                    adjustment_amount = ?, adjustment_note = ?, deduction_total = ?, refund_amount = ?,
                    mid_term_termination = ?, refund_status = ?, payment_method = ?, bank_code = ?,
                    bank_account = ?, bank_account_name = ?, updated_by = ?, updated_at = CURRENT_TIMESTAMP
                WHERE refund_id = ?
                """,
                request.customerId(), request.contractId(), request.chargeListId(), blankToNull(request.refundReason()),
                adjustmentAmount, blankToNull(request.adjustmentNote()), deductionTotal, refundAmount,
                midTermTermination, nextStatus, blankToNull(request.paymentMethod()), blankToNull(request.bankCode()),
                blankToNull(request.bankAccount()), blankToNull(request.bankAccountName()),
                staffId, id);

        Map<String, Object> result = refundDetail(id);
        String message = refundMessage(refundBase);
        if (message != null) {
            result.put("message", message);
        }
        return result;
    }

    public Map<String, Object> cancelRefund(long id, RefundCancelRequest request) {
        Map<String, Object> current = refundDetail(id);
        String currentStatus = (String) current.get("refund_status");
        if ("已退款".equals(currentStatus)) {
            throw new IllegalArgumentException("已退款的退款單無法取消");
        }
        if ("已取消".equals(currentStatus)) {
            throw new IllegalArgumentException("退款單已是取消狀態");
        }
        Long staffId = request.staffId() == null ? 1L : request.staffId();
        jdbc.update("""
                UPDATE refunds SET refund_status = '已取消', updated_by = ?, updated_at = CURRENT_TIMESTAMP
                WHERE refund_id = ?
                """, staffId, id);
        return refundDetail(id);
    }

    public Map<String, Object> reviewRefund(long id, RefundReviewRequest request) {
        requiredId(request.reviewerId(), "reviewerId");
        String roleName;
        try {
            roleName = jdbc.queryForObject("""
                    SELECT rp.role_name FROM staff s
                    JOIN role_permissions rp ON rp.role_permission_id = s.role_permission_id
                    WHERE s.staff_id = ?
                    """, String.class, request.reviewerId());
        } catch (EmptyResultDataAccessException e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "reviewer not found");
        }
        if (!"主管".equals(roleName)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "only 主管 can review refunds");
        }

        Map<String, Object> current = refundDetail(id);
        if (!"待審核".equals(current.get("refund_status"))) {
            throw new IllegalArgumentException("only 待審核 refunds can be reviewed");
        }

        jdbc.update("""
                UPDATE refunds
                SET refund_status = '審核通過', reviewed_by = ?, reviewed_at = CURRENT_TIMESTAMP,
                    updated_by = ?, updated_at = CURRENT_TIMESTAMP
                WHERE refund_id = ?
                """, request.reviewerId(), request.reviewerId(), id);
        return refundDetail(id);
    }

    public Map<String, Object> importChargeList(ImportChargeListRequest request) {
        requiredId(request.chargeListId(), "chargeListId");
        Map<String, Object> chargeList;
        try {
            chargeList = jdbc.queryForMap("SELECT * FROM charge_lists WHERE charge_list_id = ?", request.chargeListId());
        } catch (EmptyResultDataAccessException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "charge list not found");
        }
        Long customerId = ((Number) chargeList.get("customer_id")).longValue();
        Long contractId = chargeList.get("contract_id") == null ? null : ((Number) chargeList.get("contract_id")).longValue();
        BigDecimal deductionTotal = zeroIfNull((BigDecimal) chargeList.get("total_amount"));
        BigDecimal baseAmount = contractId == null ? BigDecimal.ZERO : contractDeposit(contractId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("customerId", customerId);
        result.put("contractId", contractId);
        result.put("chargeListId", request.chargeListId());
        result.put("baseAmount", baseAmount);
        result.put("deductionTotal", deductionTotal);
        return result;
    }

    private void markChargeListSettled(long chargeListId, Long staffId) {
        jdbc.update("""
                UPDATE charge_lists SET status = 1, updated_by = ?, updated_at = CURRENT_TIMESTAMP
                WHERE charge_list_id = ?
                """, staffId, chargeListId);
    }

    private String refundListSql() {
        return """
                SELECT r.*, c.company_name AS matched_company_name, c.tax_id,
                       co.deposit AS contract_deposit,
                       co.end_date_text AS contract_end_date_text,
                       s1.staff_name AS created_by_name,
                       s2.staff_name AS reviewed_by_name,
                       s3.staff_name AS termination_staff_name
                FROM refunds r
                LEFT JOIN customers c ON c.customer_id = r.customer_id
                LEFT JOIN contracts co ON co.contract_id = r.contract_id
                LEFT JOIN staff s1 ON s1.staff_id = r.created_by
                LEFT JOIN staff s2 ON s2.staff_id = r.reviewed_by
                LEFT JOIN staff s3 ON s3.staff_id = r.termination_staff_id
                """;
    }

    private record RefundBase(BigDecimal amount, boolean midTermApplied) {
    }

    /**
     * 秘書於退款申請勾選「中途解約」時，押金基準為押金的一半（若合約未收押金，押金為 0，基準也是 0，等於沒有可退的押金）；
     * 未勾選（合約到期解約）則維持全額押金。月租金額不參與這個判斷。
     */
    private RefundBase refundBaseAmount(Long contractId, boolean midTermTermination) {
        BigDecimal deposit = zeroIfNull(jdbc.queryForObject(
                "SELECT deposit FROM contracts WHERE contract_id = ?", BigDecimal.class, contractId));
        if (midTermTermination) {
            BigDecimal half = deposit.divide(BigDecimal.valueOf(2), 2, java.math.RoundingMode.HALF_UP);
            return new RefundBase(half, deposit.compareTo(BigDecimal.ZERO) > 0);
        }
        return new RefundBase(deposit, false);
    }

    private BigDecimal contractDeposit(Long contractId) {
        BigDecimal deposit = jdbc.queryForObject("SELECT deposit FROM contracts WHERE contract_id = ?",
                BigDecimal.class, contractId);
        return zeroIfNull(deposit);
    }

    private String refundMessage(RefundBase refundBase) {
        if (refundBase.midTermApplied()) {
            return "中途解約，退款金額已依規定以押金的一半（NT$" + refundBase.amount() + "）為基準計算。";
        }
        return null;
    }

    /**
     * 超額扣款時退款單直接擋下（不落地存 0），並附上收費清單資訊導引秘書至收費清單管理確認收款，
     * 退款與收款流程互不關聯。
     */
    private void requireNotOverDeducted(BigDecimal refundAmount, Long chargeListId, Long customerId, Long contractId) {
        if (refundAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new RefundOverDeductedException("欠款大於可退押金，請先確認收款金額", chargeListId, customerId, contractId);
        }
    }

    private void requirePaymentInfo(RefundRequest request) {
        requireNonBlank(request.paymentMethod(), "paymentMethod");
        requireNonBlank(request.bankCode(), "bankCode");
        requireNonBlank(request.bankAccount(), "bankAccount");
        requireNonBlank(request.bankAccountName(), "bankAccountName");
    }

    private void requireNonBlank(String value, String fieldName) {
        if (blankToNull(value) == null) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }

    private void requireExistingCustomer(Long customerId) {
        requiredId(customerId, "customerId");
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM customers WHERE customer_id = ?", Integer.class, customerId);
        if (count == null || count == 0) {
            throw new IllegalArgumentException("customerId not found");
        }
    }

    private void requireExistingContract(Long contractId) {
        requiredId(contractId, "contractId");
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM contracts WHERE contract_id = ?", Integer.class, contractId);
        if (count == null || count == 0) {
            throw new IllegalArgumentException("contractId not found");
        }
    }

    private void requireContractBelongsToCustomer(Long contractId, Long customerId) {
        Long actualCustomerId = jdbc.queryForObject("SELECT customer_id FROM contracts WHERE contract_id = ?", Long.class, contractId);
        if (actualCustomerId == null || !actualCustomerId.equals(customerId)) {
            throw new IllegalArgumentException("contractId does not belong to customerId");
        }
    }

    private void requireNonNegative(BigDecimal value, String fieldName) {
        if (value != null && value.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(fieldName + " must not be negative");
        }
    }

    private BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
