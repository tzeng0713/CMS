package com.example.cms.service;

import com.example.cms.dto.SalesTargetRequest;
import com.example.cms.service.support.CmsJdbcSupport;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SalesTargetService extends CmsJdbcSupport {

    public SalesTargetService(JdbcTemplate jdbc) {
        super(jdbc);
    }

    public Map<String, Object> list(Long branchId, Integer targetMonth, String category, Integer page, Integer pageSize) {
        String categoryText = blankToNull(category);

        String where = """
                 WHERE (? IS NULL OR st.branch_id = ?)
                   AND (? IS NULL OR st.target_month = ?)
                   AND (? IS NULL OR st.category = ?)
                """;
        List<Object> args = new ArrayList<>();
        args.add(branchId); args.add(branchId);
        args.add(targetMonth); args.add(targetMonth);
        args.add(categoryText); args.add(categoryText);

        int size = pageSize == null || pageSize <= 0 ? 20 : Math.min(pageSize, 200);
        int pageNumber = page == null || page < 0 ? 0 : page;

        List<Object> selectArgs = new ArrayList<>(args);
        selectArgs.add(size);
        selectArgs.add(pageNumber * size);
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT st.*, b.branch_name, s1.staff_name AS created_by_name
                FROM sales_targets st
                JOIN branches b ON b.branch_id = st.branch_id
                LEFT JOIN staff s1 ON s1.staff_id = st.created_by
                """ + where + " ORDER BY st.target_month DESC, st.category LIMIT ? OFFSET ?",
                selectArgs.toArray());

        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sales_targets st" + where, Long.class, args.toArray());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("content", rows);
        result.put("totalElements", total);
        result.put("page", pageNumber);
        result.put("pageSize", size);
        return result;
    }

    public Map<String, Object> create(SalesTargetRequest request) {
        requireManager(request.staffId());

        Long branchId = requiredId(request.branchId(), "branchId");
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM branches WHERE branch_id = ?", Integer.class, branchId);
        if (count == null || count == 0) {
            throw new IllegalArgumentException("branchId not found");
        }
        Integer targetMonth = request.targetMonth();
        if (targetMonth == null || String.valueOf(targetMonth).length() != 6) {
            throw new IllegalArgumentException("targetMonth must be in YYYYMM format");
        }
        String category = blankToNull(request.category());
        if (category == null) {
            throw new IllegalArgumentException("category is required");
        }
        Integer targetCount = request.targetCount();
        if (targetCount == null || targetCount < 0) {
            throw new IllegalArgumentException("targetCount must not be negative");
        }

        Integer duplicate = jdbc.queryForObject("""
                SELECT COUNT(*) FROM sales_targets
                WHERE branch_id = ? AND target_month = ? AND category = ?
                """, Integer.class, branchId, targetMonth, category);
        if (duplicate != null && duplicate > 0) {
            throw new IllegalArgumentException("此分館/月份/類別的業績目標已存在");
        }

        Long id = nextId("sales_targets", "sales_target_id");
        jdbc.update("""
                INSERT INTO sales_targets (sales_target_id, branch_id, target_month, category, target_count, created_by, created_at)
                VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """, id, branchId, targetMonth, category, targetCount, request.staffId());

        return jdbc.queryForMap("""
                SELECT st.*, b.branch_name
                FROM sales_targets st JOIN branches b ON b.branch_id = st.branch_id
                WHERE st.sales_target_id = ?
                """, id);
    }

    private void requireManager(Long staffId) {
        requiredId(staffId, "staffId");
        String roleName;
        try {
            roleName = jdbc.queryForObject("""
                    SELECT rp.role_name FROM staff s
                    JOIN role_permissions rp ON rp.role_permission_id = s.role_permission_id
                    WHERE s.staff_id = ?
                    """, String.class, staffId);
        } catch (EmptyResultDataAccessException e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "staff not found");
        }
        if (!"主管".equals(roleName)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "only 主管 can create sales targets");
        }
    }
}
