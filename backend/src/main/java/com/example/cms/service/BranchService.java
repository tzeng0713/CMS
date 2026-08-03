package com.example.cms.service;

import com.example.cms.dto.BranchRequest;
import com.example.cms.service.support.CmsJdbcSupport;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

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
        BranchRequest normalized = validate(request);
        Long id = nextId("branches", "branch_id");
        jdbc.update("""
                INSERT INTO branches
                    (branch_id, branch_name, branch_code, branch_address, tax_id, bank_account, bank_branch, bank_account_name)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                id, normalized.branchName(), normalized.branchCode(), normalized.branchAddress(), normalized.taxId(),
                normalized.bankAccount(), normalized.bankBranch(), normalized.bankAccountName());
        return jdbc.queryForMap("SELECT * FROM branches WHERE branch_id = ?", id);
    }

    public Map<String, Object> updateBranch(long id, BranchRequest request) {
        BranchRequest normalized = validate(request);
        jdbc.update("""
                UPDATE branches
                SET branch_name = ?, branch_code = ?, branch_address = ?, tax_id = ?,
                    bank_account = ?, bank_branch = ?, bank_account_name = ?
                WHERE branch_id = ?
                """,
                normalized.branchName(), normalized.branchCode(), normalized.branchAddress(), normalized.taxId(),
                normalized.bankAccount(), normalized.bankBranch(), normalized.bankAccountName(), id);
        return jdbc.queryForMap("SELECT * FROM branches WHERE branch_id = ?", id);
    }

    private BranchRequest validate(BranchRequest request) {
        String name = requiredBranchName(request.branchName());
        String branchCode = optionalPattern(request.branchCode(), "branchCode", 50, "^\\d+$");
        String branchAddress = optionalPattern(request.branchAddress(), "branchAddress", 255, null);
        String taxId = optionalPattern(request.taxId(), "taxId", 8, "^\\d{8}$");
        String bankAccount = optionalPattern(request.bankAccount(), "bankAccount", 30, "^\\d+$");
        String bankBranch = optionalPattern(request.bankBranch(), "bankBranch", 100, null);
        String bankAccountName = optionalPattern(request.bankAccountName(), "bankAccountName", 100, null);
        return new BranchRequest(name, branchCode, branchAddress, taxId, bankAccount, bankBranch, bankAccountName);
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
}
