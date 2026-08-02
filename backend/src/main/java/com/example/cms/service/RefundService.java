package com.example.cms.service;

import com.example.cms.service.support.CmsJdbcSupport;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class RefundService extends CmsJdbcSupport {

    public RefundService(JdbcTemplate jdbc) {
        super(jdbc);
    }

    public List<Map<String, Object>> refunds() {
        return jdbc.queryForList("""
                SELECT r.*, c.company_name AS matched_company_name
                FROM refunds r
                LEFT JOIN customers c ON c.customer_id = r.customer_id
                ORDER BY r.refund_id DESC
                LIMIT 200
                """);
    }
}
