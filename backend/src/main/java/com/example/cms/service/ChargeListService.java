package com.example.cms.service;

import com.example.cms.dto.ChargeListRequest;
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
public class ChargeListService extends CmsJdbcSupport {

    private static final Map<String, String> SORT_COLUMNS = Map.of(
            "chargeListId", "cl.charge_list_id",
            "customerId", "cl.customer_id",
            "feeMonth", "cl.fee_month",
            "totalAmount", "cl.total_amount",
            "status", "cl.status",
            "issuedAt", "cl.issued_at"
    );

    public ChargeListService(JdbcTemplate jdbc) {
        super(jdbc);
    }

    public Map<String, Object> chargeLists(Long chargeListId, Long customerId, Long contractId,
                                           String feeMonth, Integer status,
                                           Long createdBy, String issuedFrom, String issuedTo,
                                           Integer page, Integer pageSize, String sortBy, String sortDir) {
        String feeMonthText = blankToEmpty(feeMonth);
        String issuedFromText = blankToNull(issuedFrom);
        String issuedToText = blankToNull(issuedTo);

        String where = """
                 WHERE (? IS NULL OR cl.charge_list_id = ?)
                   AND (? IS NULL OR cl.customer_id = ?)
                   AND (? IS NULL OR cl.contract_id = ?)
                   AND (? = '' OR cl.fee_month = ?)
                   AND (? IS NULL OR cl.status = ?)
                   AND (? IS NULL OR cl.created_by = ?)
                   AND (? IS NULL OR DATE(cl.issued_at) >= ?)
                   AND (? IS NULL OR DATE(cl.issued_at) <= ?)
                """;
        List<Object> args = new ArrayList<>();
        args.add(chargeListId); args.add(chargeListId);
        args.add(customerId); args.add(customerId);
        args.add(contractId); args.add(contractId);
        args.add(feeMonthText); args.add(feeMonthText);
        args.add(status); args.add(status);
        args.add(createdBy); args.add(createdBy);
        args.add(issuedFromText); args.add(issuedFromText);
        args.add(issuedToText); args.add(issuedToText);

        String sortColumn = sortBy == null ? "cl.issued_at" : SORT_COLUMNS.getOrDefault(sortBy, "cl.issued_at");
        String direction = "asc".equalsIgnoreCase(sortDir) ? "ASC" : "DESC";
        int size = pageSize == null || pageSize <= 0 ? 20 : Math.min(pageSize, 200);
        int pageNumber = page == null || page < 0 ? 0 : page;

        List<Object> selectArgs = new ArrayList<>(args);
        selectArgs.add(size);
        selectArgs.add(pageNumber * size);
        List<Map<String, Object>> rows = jdbc.queryForList(
                chargeListListSql() + where + " ORDER BY " + sortColumn + " " + direction + " LIMIT ? OFFSET ?",
                selectArgs.toArray());

        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM charge_lists cl" + where, Long.class, args.toArray());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("content", rows);
        result.put("totalElements", total);
        result.put("page", pageNumber);
        result.put("pageSize", size);
        return result;
    }

    public Map<String, Object> chargeListDetail(long id) {
        try {
            return jdbc.queryForMap(chargeListListSql() + " WHERE cl.charge_list_id = ?", id);
        } catch (EmptyResultDataAccessException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "charge list not found");
        }
    }

    public Map<String, Object> createChargeList(ChargeListRequest request) {
        requireExistingCustomer(request.customerId());
        requireExistingContract(request.contractId());
        requireContractBelongsToCustomer(request.contractId(), request.customerId());
        requireValidMonth(request.feeMonth());
        requireNonNegativeAmounts(request);
        BigDecimal totalAmount = calculateTotalAmount(request);
        Long id = nextId("charge_lists", "charge_list_id");
        Long staffId = request.updatedBy() == null ? 1L : request.updatedBy();
        jdbc.update("""
                INSERT INTO charge_lists (
                    charge_list_id, customer_id, contract_id, fee_month,
                    management_fee, electricity_fee, printing_fee, meeting_room_fee, tax, advance_payment, repair_fee,
                    total_amount, status, created_by, issued_at, updated_by, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 2, ?, CURRENT_TIMESTAMP, ?, CURRENT_TIMESTAMP)
                """,
                id, request.customerId(), request.contractId(), request.feeMonth(),
                zeroIfNull(request.managementFee()), zeroIfNull(request.electricityFee()), zeroIfNull(request.printingFee()),
                zeroIfNull(request.meetingRoomFee()), zeroIfNull(request.tax()), zeroIfNull(request.advancePayment()),
                zeroIfNull(request.repairFee()), totalAmount, staffId, staffId);
        return chargeListDetail(id);
    }

    public Map<String, Object> updateChargeList(long id, ChargeListRequest request) {
        requireValidMonth(request.feeMonth());
        requireNonNegativeAmounts(request);
        BigDecimal totalAmount = calculateTotalAmount(request);
        Long staffId = request.updatedBy() == null ? 1L : request.updatedBy();
        int updated = jdbc.update("""
                UPDATE charge_lists
                SET fee_month = ?,
                    management_fee = ?,
                    electricity_fee = ?,
                    printing_fee = ?,
                    meeting_room_fee = ?,
                    tax = ?,
                    advance_payment = ?,
                    repair_fee = ?,
                    total_amount = ?,
                    updated_by = ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE charge_list_id = ?
                """,
                request.feeMonth(),
                zeroIfNull(request.managementFee()), zeroIfNull(request.electricityFee()), zeroIfNull(request.printingFee()),
                zeroIfNull(request.meetingRoomFee()), zeroIfNull(request.tax()), zeroIfNull(request.advancePayment()),
                zeroIfNull(request.repairFee()), totalAmount, staffId, id);
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "charge list not found");
        }
        return chargeListDetail(id);
    }

    private String chargeListListSql() {
        return """
                SELECT cl.*, c.company_name, o.office_no, b.branch_name,
                       b.bank_account, b.bank_branch, b.bank_account_name,
                       co.rent AS contract_rent, s.staff_name AS created_by_name
                FROM charge_lists cl
                JOIN customers c ON c.customer_id = cl.customer_id
                LEFT JOIN contracts co ON co.contract_id = cl.contract_id
                LEFT JOIN offices o ON o.office_id = co.office_id
                LEFT JOIN branches b ON b.branch_id = o.branch_id
                LEFT JOIN staff s ON s.staff_id = cl.created_by
                """;
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

    private void requireValidMonth(String feeMonth) {
        requiredMonth(feeMonth, "feeMonth");
    }

    private String requiredMonth(String value, String fieldName) {
        if (value == null || !value.matches("\\d{4}-\\d{2}")) {
            throw new IllegalArgumentException(fieldName + " must be in YYYY-MM format");
        }
        return value;
    }

    private void requireNonNegativeAmounts(ChargeListRequest request) {
        requireNonNegative(request.managementFee(), "managementFee");
        requireNonNegative(request.electricityFee(), "electricityFee");
        requireNonNegative(request.printingFee(), "printingFee");
        requireNonNegative(request.meetingRoomFee(), "meetingRoomFee");
        requireNonNegative(request.tax(), "tax");
        requireNonNegative(request.advancePayment(), "advancePayment");
        requireNonNegative(request.repairFee(), "repairFee");
    }

    private void requireNonNegative(BigDecimal value, String fieldName) {
        if (value != null && value.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(fieldName + " must not be negative");
        }
    }

    private BigDecimal calculateTotalAmount(ChargeListRequest request) {
        return zeroIfNull(request.managementFee())
                .add(zeroIfNull(request.electricityFee()))
                .add(zeroIfNull(request.printingFee()))
                .add(zeroIfNull(request.meetingRoomFee()))
                .add(zeroIfNull(request.tax()))
                .add(zeroIfNull(request.advancePayment()))
                .add(zeroIfNull(request.repairFee()));
    }

    private BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
