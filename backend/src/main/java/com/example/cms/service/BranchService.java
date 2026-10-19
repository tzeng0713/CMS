package com.example.cms.service;

import com.example.cms.dto.BranchRequest;
import com.example.cms.service.support.CmsJdbcSupport;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@Service
public class BranchService extends CmsJdbcSupport {

    public BranchService(JdbcTemplate jdbc) {
        super(jdbc);
    }

    public List<Map<String, Object>> branches() {
        return jdbc.queryForList("SELECT * FROM branches ORDER BY branch_id");
    }

    public Map<String, Object> createBranch(BranchRequest request) {
        requireManager(request.staffId());
        BranchRequest normalized = validate(request);
        Long id = nextId("branches", "branch_id");
        jdbc.update("""
                INSERT INTO branches
                    (branch_id, branch_name, company_name, branch_code, branch_address, tax_id, bank_account, bank_branch, bank_account_name)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                id, normalized.branchName(), normalized.companyName(), normalized.branchCode(), normalized.branchAddress(),
                normalized.taxId(), normalized.bankAccount(), normalized.bankBranch(), normalized.bankAccountName());
        return jdbc.queryForMap("SELECT * FROM branches WHERE branch_id = ?", id);
    }

    public Map<String, Object> updateBranch(long id, BranchRequest request) {
        requireCanEditBranch(request.staffId(), id);
        BranchRequest normalized = validate(request);
        jdbc.update("""
                UPDATE branches
                SET branch_name = ?, company_name = ?, branch_code = ?, branch_address = ?, tax_id = ?,
                    bank_account = ?, bank_branch = ?, bank_account_name = ?
                WHERE branch_id = ?
                """,
                normalized.branchName(), normalized.companyName(), normalized.branchCode(), normalized.branchAddress(),
                normalized.taxId(), normalized.bankAccount(), normalized.bankBranch(), normalized.bankAccountName(), id);
        return jdbc.queryForMap("SELECT * FROM branches WHERE branch_id = ?", id);
    }

    private BranchRequest validate(BranchRequest request) {
        String name = requiredBranchName(request.branchName());
        String companyName = optionalPattern(request.companyName(), "companyName", 100, null);
        String branchCode = optionalPattern(request.branchCode(), "branchCode", 50, "^\\d+$");
        String branchAddress = optionalPattern(request.branchAddress(), "branchAddress", 255, null);
        String taxId = optionalPattern(request.taxId(), "taxId", 8, "^\\d{8}$");
        String bankAccount = optionalPattern(request.bankAccount(), "bankAccount", 30, "^\\d+$");
        String bankBranch = optionalPattern(request.bankBranch(), "bankBranch", 100, null);
        String bankAccountName = optionalPattern(request.bankAccountName(), "bankAccountName", 100, null);
        return new BranchRequest(name, companyName, branchCode, branchAddress, taxId, bankAccount, bankBranch,
                bankAccountName, request.staffId());
    }

    private String requiredBranchName(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("branchName is required");
        }
        String trimmed = value.trim();
        if (trimmed.length() > 100) {
            throw new IllegalArgumentException("branchName must be at most 100 characters");
        }
        return trimmed;
    }

    private record StaffAuthority(String roleName, Long branchId) {
    }

    private StaffAuthority staffAuthority(Long staffId) {
        if (staffId == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "staffId is required");
        }
        try {
            return jdbc.queryForObject("""
                    SELECT rp.role_name AS role_name, s.branch_id AS branch_id
                    FROM staff s
                    JOIN role_permissions rp ON rp.role_permission_id = s.role_permission_id
                    WHERE s.staff_id = ?
                    """,
                    (rs, rowNum) -> new StaffAuthority(rs.getString("role_name"),
                            rs.getObject("branch_id") == null ? null : rs.getLong("branch_id")),
                    staffId);
        } catch (EmptyResultDataAccessException e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "staff not found");
        }
    }

    private void requireManager(Long staffId) {
        StaffAuthority authority = staffAuthority(staffId);
        if (!"主管".equals(authority.roleName())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "only 主管 can create branches");
        }
    }

    private void requireCanEditBranch(Long staffId, long branchId) {
        StaffAuthority authority = staffAuthority(staffId);
        if ("主管".equals(authority.roleName())) {
            return;
        }
        if (authority.branchId() == null || authority.branchId() != branchId) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "can only edit your own branch");
        }
    }
}
