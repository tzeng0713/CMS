package com.example.cms.service;

import com.example.cms.service.support.CmsJdbcSupport;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class MetadataService extends CmsJdbcSupport {
    @Autowired(required = false)
    private RoleAccess access;

    public MetadataService(JdbcTemplate jdbc) {
        super(jdbc);
    }

    public Map<String, Object> metadata() {
        Long ownBranch = access != null && access.actor().generalSecretary() ? access.actor().branchId() : null;
        Long ownPerformanceBranch = access != null && !access.actor().manager() ? access.actor().branchId() : null;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("branches", jdbc.queryForList("SELECT * FROM branches ORDER BY branch_id"));
        result.put("owners", jdbc.queryForList("""
                SELECT owner_name, COUNT(*) AS customer_count
                FROM customers
                WHERE owner_name IS NOT NULL AND owner_name <> ''
                  AND (? IS NULL OR EXISTS (SELECT 1 FROM contracts co JOIN offices o ON o.office_id = co.office_id
                       WHERE co.customer_id = customers.customer_id AND o.branch_id = ?))
                GROUP BY owner_name
                ORDER BY owner_name
                """, ownBranch, ownBranch));
        result.put("roles", jdbc.queryForList("SELECT * FROM role_permissions ORDER BY role_permission_id"));
        result.put("staff", jdbc.queryForList("""
                SELECT s.staff_id, s.staff_name, s.branch_id, r.role_name, b.branch_name
                FROM staff s
                JOIN role_permissions r ON r.role_permission_id = s.role_permission_id
                LEFT JOIN branches b ON b.branch_id = s.branch_id
                ORDER BY s.staff_id
                """));
        result.put("salesTargets", jdbc.queryForList("""
                SELECT st.sales_target_id, st.branch_id, st.target_month AS `month`,
                       st.category, st.target_count, b.branch_name
                FROM sales_targets st JOIN branches b ON b.branch_id = st.branch_id
                WHERE (? IS NULL OR st.branch_id = ?)
                ORDER BY st.target_month, st.category
                """, ownPerformanceBranch, ownPerformanceBranch));
        return result;
    }
}
