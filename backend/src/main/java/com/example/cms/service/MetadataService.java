package com.example.cms.service;

import com.example.cms.service.support.CmsJdbcSupport;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class MetadataService extends CmsJdbcSupport {

    public MetadataService(JdbcTemplate jdbc) {
        super(jdbc);
    }

    public Map<String, Object> metadata() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("branches", jdbc.queryForList("SELECT * FROM branches ORDER BY branch_id"));
        result.put("owners", jdbc.queryForList("""
                SELECT owner_name, COUNT(*) AS customer_count
                FROM customers
                WHERE owner_name IS NOT NULL AND owner_name <> ''
                GROUP BY owner_name
                ORDER BY owner_name
                """));
        result.put("roles", jdbc.queryForList("SELECT * FROM role_permissions ORDER BY role_permission_id"));
        result.put("staff", jdbc.queryForList("""
                SELECT s.*, r.role_name, b.branch_name
                FROM staff s
                JOIN role_permissions r ON r.role_permission_id = s.role_permission_id
                LEFT JOIN branches b ON b.branch_id = s.branch_id
                ORDER BY s.staff_id
                """));
        result.put("salesTargets", jdbc.queryForList("""
                SELECT st.sales_target_id, st.branch_id, st.target_month AS `month`,
                       st.category, st.target_count, b.branch_name
                FROM sales_targets st JOIN branches b ON b.branch_id = st.branch_id
                ORDER BY st.target_month, st.category
                """));
        return result;
    }
}
