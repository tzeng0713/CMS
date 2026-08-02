package com.example.cms.service;

import com.example.cms.dto.ContractRequest;
import com.example.cms.service.support.CmsJdbcSupport;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;

@Service
public class ContractService extends CmsJdbcSupport {

    public ContractService(JdbcTemplate jdbc) {
        super(jdbc);
    }

    public List<Map<String, Object>> contracts(String search, String companyName, String startDateText, String endDateText, String leaseStatus) {
        String searchText = search == null ? "" : search.trim();
        String like = "%" + searchText + "%";
        String companyLike = "%" + (companyName == null ? "" : companyName.trim()) + "%";
        String startText = startDateText == null ? "" : startDateText.trim();
        String endText = endDateText == null ? "" : endDateText.trim();
        String statusText = leaseStatus == null ? "" : leaseStatus.trim();
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT co.*, c.company_name, c.owner_name, c.tax_id, o.office_no, b.branch_name,
                       s.staff_name AS signer_staff_name, partner.staff_name AS partner_staff_name
                FROM contracts co
                JOIN customers c ON c.customer_id = co.customer_id
                LEFT JOIN offices o ON o.office_id = co.office_id
                LEFT JOIN branches b ON b.branch_id = o.branch_id
                LEFT JOIN staff s ON s.staff_id = co.signer_staff_id
                LEFT JOIN staff partner ON partner.staff_id = co.partner_staff_id
                WHERE (? = '%%'
                   OR c.company_name LIKE ?
                   OR c.owner_name LIKE ?
                   OR c.tax_id LIKE ?
                   OR o.office_no LIKE ?
                   OR co.lease_status LIKE ?)
                  AND (? = '%%' OR c.company_name LIKE ?)
                  AND (? = '' OR co.lease_status = ?)
                ORDER BY co.contract_id DESC
                LIMIT 1000
                """, like, like, like, like, like, like,
                companyLike, companyLike,
                statusText, statusText);
        return rows.stream()
                .filter(row -> contractOverlaps(row, startText, endText))
                .limit(200)
                .toList();
    }

    public Map<String, Object> createContract(ContractRequest request) {
        validateContractStaff(request);
        Long id = nextId("contracts", "contract_id");
        jdbc.update("""
                INSERT INTO contracts (
                    contract_id, customer_id, office_id, rental_item, rental_status, signed_date_text,
                    signer_staff_id, partner_staff_id, source_text, payment_months,
                    start_date_text, end_date_text, termination_date_text,
                    rent, deposit, lease_image_path, lease_status, updated_by, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """,
                id,
                requiredId(request.customerId(), "customerId"),
                request.officeId(),
                normalizeContractRentalItem(request.rentalItem()),
                normalizeContractRentalStatus(request.rentalStatus()),
                blankToNull(request.signedDateText()),
                request.signerStaffId(),
                request.partnerStaffId(),
                blankToNull(request.sourceText()),
                request.paymentMonths(),
                blankToNull(request.startDateText()),
                blankToNull(request.endDateText()),
                blankToNull(request.terminationDateText()),
                request.rent(),
                request.deposit(),
                blankToNull(request.leaseImagePath()),
                normalizeContractStatus(request.leaseStatus()),
                request.updatedBy() == null ? 1L : request.updatedBy());
        return contractDetail(id);
    }

    public Map<String, Object> updateContract(long id, ContractRequest request) {
        jdbc.update("""
                UPDATE contracts
                SET lease_status = ?,
                    updated_by = ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE contract_id = ?
                """,
                normalizeContractStatus(request.leaseStatus()),
                request.updatedBy() == null ? 1L : request.updatedBy(),
                id);
        return contractDetail(id);
    }

    public Map<String, Object> latestContract(long customerId) {
        List<Long> ids = jdbc.queryForList("""
                SELECT contract_id
                FROM contracts
                WHERE customer_id = ?
                ORDER BY contract_id DESC
                LIMIT 1
                """, Long.class, customerId);
        return ids.isEmpty() ? Map.of() : contractDetail(ids.get(0));
    }

    void updateLeaseImagePath(long contractId, String imagePath) {
        jdbc.update("UPDATE contracts SET lease_image_path = ? WHERE contract_id = ?", imagePath, contractId);
    }

    String saveLeaseImage(Long contractId, MultipartFile file) {
        String originalName = file.getOriginalFilename() == null ? "contract-image" : file.getOriginalFilename();
        String safeName = originalName.replaceAll("[^a-zA-Z0-9._-]", "_");
        String fileName = "contract-" + contractId + "-" + System.currentTimeMillis() + "-" + safeName;
        Path uploadDir = Path.of("uploads", "contracts");
        Path target = uploadDir.resolve(fileName).normalize();
        try {
            Files.createDirectories(uploadDir);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            return uploadDir.resolve(fileName).toString().replace('\\', '/');
        } catch (IOException e) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "lease image upload failed", e);
        }
    }

    private Map<String, Object> contractDetail(long id) {
        return jdbc.queryForMap("""
                SELECT co.*, c.company_name, c.owner_name, c.tax_id, o.office_no, b.branch_name,
                       s.staff_name AS signer_staff_name, partner.staff_name AS partner_staff_name
                FROM contracts co
                JOIN customers c ON c.customer_id = co.customer_id
                LEFT JOIN offices o ON o.office_id = co.office_id
                LEFT JOIN branches b ON b.branch_id = o.branch_id
                LEFT JOIN staff s ON s.staff_id = co.signer_staff_id
                LEFT JOIN staff partner ON partner.staff_id = co.partner_staff_id
                WHERE co.contract_id = ?
                """, id);
    }

    private boolean contractOverlaps(Map<String, Object> row, String queryStartText, String queryEndText) {
        Integer queryStart = dateNumber(queryStartText);
        Integer queryEnd = dateNumber(queryEndText);
        if (queryStart == null && queryEnd == null) {
            return true;
        }
        Integer contractStart = dateNumber((String) row.get("start_date_text"));
        Integer contractEnd = dateNumber((String) row.get("end_date_text"));
        if (queryStart != null && contractEnd != null && queryStart > contractEnd) {
            return false;
        }
        return queryEnd == null || contractStart == null || queryEnd >= contractStart;
    }

    String normalizeContractRentalItem(String value) {
        String item = blankToNull(value);
        if (item == null) {
            return null;
        }
        if ("實體辦公室".equals(item)) {
            return "辦公室";
        }
        if (!List.of("辦公室", "座位", "登記", "聯絡處", "停業").contains(item)) {
            throw new IllegalArgumentException("rentalItem must be 辦公室, 座位, 登記, 聯絡處, or 停業");
        }
        return item;
    }

    private String normalizeContractRentalStatus(String value) {
        String status = blankToNull(value);
        if (status == null) {
            return null;
        }
        if (!List.of("登記", "辦公室", "登記+辦公室", "個人名義").contains(status)) {
            throw new IllegalArgumentException("rentalStatus must be 登記, 辦公室, 登記+辦公室, or 個人名義");
        }
        return status;
    }

    private String normalizeContractStatus(String value) {
        if (value == null || value.isBlank()) {
            return "綁約中";
        }
        String status = value.trim();
        if (!"綁約中".equals(status) && !"已解約".equals(status)) {
            throw new IllegalArgumentException("leaseStatus must be 綁約中 or 已解約");
        }
        return status;
    }

    private void validateContractStaff(ContractRequest request) {
        if (request.signerStaffId() != null
                && request.signerStaffId().equals(request.partnerStaffId())) {
            throw new IllegalArgumentException("signerStaffId and partnerStaffId must be different");
        }
    }
}
