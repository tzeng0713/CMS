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
        String name = requiredBranchName(request.branchName());
        Long id = nextId("branches", "branch_id");
        jdbc.update("INSERT INTO branches (branch_id, branch_name) VALUES (?, ?)", id, name);
        return jdbc.queryForMap("SELECT * FROM branches WHERE branch_id = ?", id);
    }

    public Map<String, Object> updateBranch(long id, BranchRequest request) {
        jdbc.update("UPDATE branches SET branch_name = ? WHERE branch_id = ?",
                requiredBranchName(request.branchName()), id);
        return jdbc.queryForMap("SELECT * FROM branches WHERE branch_id = ?", id);
    }

    private String requiredBranchName(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("branchName is required");
        }
        return value.trim();
    }
}
