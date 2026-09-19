package com.example.cms.service;

import com.example.cms.dto.StaffUpdateRequest;
import com.example.cms.service.support.CmsJdbcSupport;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
            return jdbc.queryForList(staffListSql() + " WHERE s.branch_id = ?" + staffOrderSql(), branchId);
        }
        return jdbc.queryForList(staffListSql() + staffOrderSql());
    }

    public Map<String, Object> updateStaff(long id, StaffUpdateRequest request) {
        if (request.rolePermissionId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "rolePermissionId is required");
        }
        String email = request.email() == null ? null : request.email().trim();
        if (email != null && !email.isBlank()) {
            if (!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "email is invalid");
            }
            Integer duplicate = jdbc.queryForObject("SELECT COUNT(*) FROM staff WHERE LOWER(email) = LOWER(?) AND staff_id <> ?",
                    Integer.class, email, id);
            if (duplicate != null && duplicate > 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "email already exists");
            }
        }
        jdbc.update("""
                UPDATE staff
                SET role_permission_id = ?, email = COALESCE(?, email)
                WHERE staff_id = ?
                """, request.rolePermissionId(), email == null || email.isBlank() ? null : email, id);
        return jdbc.queryForMap(staffListSql() + " WHERE s.staff_id = ?", id);
    }

    @Transactional
    public Map<String, Object> approveStaff(long id, Long approvedByStaffId) {
        if (approvedByStaffId == null || !isActiveManager(approvedByStaffId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "manager approval is required");
        }
        Integer existing = jdbc.queryForObject("SELECT COUNT(*) FROM staff WHERE staff_id = ?", Integer.class, id);
        if (existing == null || existing == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "staff not found");
        }
        int updated = jdbc.update("""
                UPDATE staff
                SET account_approved_at = CURRENT_TIMESTAMP,
                    account_approved_by = ?
                WHERE staff_id = ?
                  AND email_verified_at IS NOT NULL
                  AND account_approved_at IS NULL
                """, approvedByStaffId, id);
        if (updated != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "only a verified account awaiting approval can be approved");
        }
        return jdbc.queryForMap(staffListSql() + " WHERE s.staff_id = ?", id);
    }

    private boolean isActiveManager(long staffId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM staff s
                JOIN role_permissions r ON r.role_permission_id = s.role_permission_id
                WHERE s.staff_id = ?
                  AND r.role_name = '主管'
                  AND s.email_verified_at IS NOT NULL
                  AND s.account_approved_at IS NOT NULL
                """, Integer.class, staffId);
        return count != null && count == 1;
    }

    private String staffListSql() {
        return """
                SELECT s.staff_id, s.staff_name, s.account, s.email, s.email_verified_at,
                       s.account_approved_at, s.account_approved_by, s.role_permission_id, s.branch_id,
                       r.role_name, b.branch_name, approver.staff_name AS account_approved_by_name,
                       CASE
                           WHEN s.email_verified_at IS NULL THEN 'EMAIL_UNVERIFIED'
                           WHEN s.account_approved_at IS NULL THEN 'PENDING_APPROVAL'
                           ELSE 'ACTIVE'
                       END AS account_status
                FROM staff s
                JOIN role_permissions r ON r.role_permission_id = s.role_permission_id
                LEFT JOIN branches b ON b.branch_id = s.branch_id
                LEFT JOIN staff approver ON approver.staff_id = s.account_approved_by
                """;
    }

    private String staffOrderSql() {
        return """
                 ORDER BY CASE
                              WHEN s.email_verified_at IS NOT NULL AND s.account_approved_at IS NULL THEN 0
                              WHEN s.email_verified_at IS NULL THEN 1
                              ELSE 2
                          END,
                          s.staff_id
                """;
    }
}
