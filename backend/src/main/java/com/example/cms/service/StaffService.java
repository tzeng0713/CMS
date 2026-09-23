package com.example.cms.service;

import com.example.cms.dto.StaffUpdateRequest;
import com.example.cms.dto.StaffProfileChangeRequest;
import com.example.cms.dto.StaffProfileChangeReviewRequest;
import com.example.cms.service.support.CmsJdbcSupport;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class StaffService extends CmsJdbcSupport {
    private final AuthService authService;

    public StaffService(JdbcTemplate jdbc, AuthService authService) {
        super(jdbc);
        this.authService = authService;
    }

    public Map<String, Object> staff(Long branchId, Integer page, Integer pageSize) {
        int size = pageSize == null || pageSize <= 0 ? 20 : Math.min(pageSize, 200);
        int pageNumber = page == null || page < 0 ? 0 : page;
        String where = branchId == null ? "" : " WHERE s.branch_id = ?";
        List<Object> filterArguments = new ArrayList<>();
        if (branchId != null) {
            filterArguments.add(branchId);
        }

        List<Object> pageArguments = new ArrayList<>(filterArguments);
        pageArguments.add(size);
        pageArguments.add(pageNumber * size);
        List<Map<String, Object>> rows = jdbc.queryForList(
                staffListSql() + where + staffOrderSql() + " LIMIT ? OFFSET ?", pageArguments.toArray());
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM staff s" + where, Long.class, filterArguments.toArray());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("content", rows);
        result.put("totalElements", total == null ? 0L : total);
        result.put("page", pageNumber);
        result.put("pageSize", size);
        return result;
    }

    public Map<String, Object> updateStaff(long id, StaffUpdateRequest request) {
        if (request.rolePermissionId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "rolePermissionId is required");
        }
        if (request.email() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "email changes must be submitted as a profile change request");
        }
        jdbc.update("""
                UPDATE staff
                SET role_permission_id = ?
                WHERE staff_id = ?
                """, request.rolePermissionId(), id);
        return jdbc.queryForMap(staffListSql() + " WHERE s.staff_id = ?", id);
    }

    @Transactional
    public Map<String, Object> requestProfileChange(long staffId, StaffProfileChangeRequest request) {
        if (request == null || request.requestedByStaffId() == null || request.requestedByStaffId() != staffId) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "a staff member can only change their own profile");
        }
        String staffName = request.staffName() == null ? "" : request.staffName().trim();
        String email = request.email() == null ? "" : request.email().trim();
        if (staffName.isBlank() || staffName.length() > 80 || !isEmail(email)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "a valid name and email are required");
        }
        Map<String, Object> current;
        try {
            current = jdbc.queryForMap("SELECT staff_name, email FROM staff WHERE staff_id = ?", staffId);
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "staff not found");
        }
        String currentName = (String) current.get("staff_name");
        String currentEmail = (String) current.get("email");
        if (staffName.equals(currentName) && email.equalsIgnoreCase(currentEmail == null ? "" : currentEmail)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "profile has not changed");
        }
        Integer duplicate = jdbc.queryForObject("""
                SELECT COUNT(*) FROM staff
                WHERE LOWER(email) = LOWER(?) AND staff_id <> ?
                """, Integer.class, email, staffId);
        if (duplicate != null && duplicate > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "email already exists");
        }
        jdbc.update("""
                UPDATE staff_profile_change_requests
                SET status = 'SUPERSEDED', reviewed_at = CURRENT_TIMESTAMP
                WHERE staff_id = ? AND status = 'PENDING'
                """, staffId);
        jdbc.update("""
                INSERT INTO staff_profile_change_requests (staff_id, requested_staff_name, requested_email)
                VALUES (?, ?, ?)
                """, staffId, staffName, email);
        return latestProfileChange(staffId);
    }

    public List<Map<String, Object>> profileChangeRequests(Long staffId, boolean pendingOnly) {
        String where = pendingOnly
                ? " WHERE p.status = 'PENDING'"
                : staffId == null ? "" : " WHERE p.staff_id = ?";
        String sql = profileChangeSql() + where + " ORDER BY CASE WHEN p.status = 'PENDING' THEN 0 ELSE 1 END, p.requested_at DESC";
        return staffId != null && !pendingOnly
                ? jdbc.queryForList(sql, staffId)
                : jdbc.queryForList(sql);
    }

    @Transactional
    public Map<String, Object> reviewProfileChange(long requestId, StaffProfileChangeReviewRequest request) {
        if (request == null || request.reviewedByStaffId() == null || request.approve() == null
                || !isActiveManager(request.reviewedByStaffId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "manager approval is required");
        }
        Map<String, Object> profileChange;
        try {
            profileChange = jdbc.queryForMap(profileChangeSql() + " WHERE p.staff_profile_change_request_id = ?", requestId);
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "profile change request not found");
        }
        if (!"PENDING".equals(profileChange.get("status"))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "profile change request is no longer pending");
        }
        long staffId = ((Number) profileChange.get("staff_id")).longValue();
        if (staffId == request.reviewedByStaffId()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "a manager cannot review their own profile change");
        }
        if (Boolean.TRUE.equals(request.approve())) {
            String requestedEmail = (String) profileChange.get("requested_email");
            String currentEmail = (String) profileChange.get("current_email");
            boolean emailChanged = !requestedEmail.equalsIgnoreCase(currentEmail == null ? "" : currentEmail);
            Integer duplicate = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM staff
                    WHERE LOWER(email) = LOWER(?) AND staff_id <> ?
                    """, Integer.class, requestedEmail, staffId);
            if (duplicate != null && duplicate > 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "email already exists");
            }
            jdbc.update("""
                    UPDATE staff
                    SET staff_name = ?,
                        email = ?,
                        email_verified_at = CASE WHEN ? THEN NULL ELSE email_verified_at END
                    WHERE staff_id = ?
                    """, profileChange.get("requested_staff_name"), requestedEmail, emailChanged, staffId);
            if (emailChanged) {
                authService.sendEmailVerification(staffId, requestedEmail);
            }
        }
        jdbc.update("""
                UPDATE staff_profile_change_requests
                SET status = ?, reviewed_at = CURRENT_TIMESTAMP, reviewed_by = ?
                WHERE staff_profile_change_request_id = ?
                """, Boolean.TRUE.equals(request.approve()) ? "APPROVED" : "REJECTED",
                request.reviewedByStaffId(), requestId);
        return jdbc.queryForMap(profileChangeSql() + " WHERE p.staff_profile_change_request_id = ?", requestId);
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

    private Map<String, Object> latestProfileChange(long staffId) {
        return jdbc.queryForMap(profileChangeSql() + " WHERE p.staff_id = ? ORDER BY p.requested_at DESC LIMIT 1", staffId);
    }

    private String profileChangeSql() {
        return """
                SELECT p.staff_profile_change_request_id, p.staff_id, p.requested_staff_name,
                       p.requested_email, p.status, p.requested_at, p.reviewed_at, p.reviewed_by,
                       s.account, s.staff_name AS current_staff_name, s.email AS current_email,
                       reviewer.staff_name AS reviewed_by_name
                FROM staff_profile_change_requests p
                JOIN staff s ON s.staff_id = p.staff_id
                LEFT JOIN staff reviewer ON reviewer.staff_id = p.reviewed_by
                """;
    }

    private boolean isEmail(String email) {
        return email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
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
