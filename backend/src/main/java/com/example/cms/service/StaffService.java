package com.example.cms.service;

import com.example.cms.dto.StaffUpdateRequest;
import com.example.cms.service.support.CmsJdbcSupport;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@Service
public class StaffService extends CmsJdbcSupport {

    public StaffService(JdbcTemplate jdbc) {
        super(jdbc);
    }

    public List<Map<String, Object>> staff(Long branchId) {
        if (branchId != null) {
            return jdbc.queryForList(staffListSql() + " WHERE s.branch_id = ? ORDER BY s.staff_id", branchId);
        }
        return jdbc.queryForList(staffListSql() + " ORDER BY s.staff_id");
    }

    public Map<String, Object> updateStaff(long id, StaffUpdateRequest request) {
        if (request.rolePermissionId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "rolePermissionId is required");
        }
        jdbc.update("""
                UPDATE staff
                SET role_permission_id = ?
                WHERE staff_id = ?
                """, request.rolePermissionId(), id);
        return jdbc.queryForMap(staffListSql() + " WHERE s.staff_id = ?", id);
    }

    private String staffListSql() {
        return """
                SELECT s.staff_id, s.staff_name, s.account, s.role_permission_id, s.branch_id,
                       r.role_name, b.branch_name
                FROM staff s
                JOIN role_permissions r ON r.role_permission_id = s.role_permission_id
                LEFT JOIN branches b ON b.branch_id = s.branch_id
                """;
    }
}
