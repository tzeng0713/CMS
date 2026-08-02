package com.example.cms.service;

import com.example.cms.dto.LoginRequest;
import com.example.cms.dto.RegisterRequest;
import com.example.cms.service.support.CmsJdbcSupport;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@Service
public class AuthService extends CmsJdbcSupport {

    public AuthService(JdbcTemplate jdbc) {
        super(jdbc);
    }

    public Map<String, Object> login(LoginRequest request) {
        if (request.account() == null || request.account().isBlank()
                || request.password() == null || request.password().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "account and password are required");
        }
        try {
            Map<String, Object> user = jdbc.queryForMap("""
                    SELECT s.staff_id, s.staff_name, s.account, s.password_hash, s.branch_id,
                           b.branch_name, r.role_permission_id, r.role_name, r.scope
                    FROM staff s
                    JOIN role_permissions r ON r.role_permission_id = s.role_permission_id
                    LEFT JOIN branches b ON b.branch_id = s.branch_id
                    WHERE s.account = ?
                    """, request.account().trim());
            if (!passwordMatches((String) user.get("password_hash"), request.password())) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid account or password");
            }
            user.remove("password_hash");
            applyPermissions(user);
            return user;
        } catch (EmptyResultDataAccessException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid account or password");
        }
    }

    public Map<String, Object> register(RegisterRequest request) {
        if (request.staffName() == null || request.staffName().isBlank()
                || request.account() == null || request.account().isBlank()
                || request.password() == null || request.password().isBlank()
                || request.roleName() == null || request.roleName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "all fields are required");
        }
        Integer duplicate = jdbc.queryForObject("SELECT COUNT(*) FROM staff WHERE account = ?",
                Integer.class, request.account().trim());
        if (duplicate != null && duplicate > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "account already exists");
        }
        Long roleId;
        try {
            roleId = jdbc.queryForObject("SELECT role_permission_id FROM role_permissions WHERE role_name = ?",
                    Long.class, request.roleName().trim());
        } catch (EmptyResultDataAccessException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid role");
        }
        Long staffId = nextId("staff", "staff_id");
        jdbc.update("""
                INSERT INTO staff (staff_id, role_permission_id, branch_id, staff_name, account, password_hash)
                VALUES (?, ?, 1, ?, ?, ?)
                """, staffId, roleId, request.staffName().trim(), request.account().trim(),
                "{noop}" + request.password());
        Map<String, Object> user = jdbc.queryForMap("""
                SELECT s.staff_id, s.staff_name, s.account, s.branch_id,
                       b.branch_name, r.role_permission_id, r.role_name, r.scope
                FROM staff s
                JOIN role_permissions r ON r.role_permission_id = s.role_permission_id
                LEFT JOIN branches b ON b.branch_id = s.branch_id
                WHERE s.staff_id = ?
                """, staffId);
        applyPermissions(user);
        return user;
    }

    private boolean passwordMatches(String storedPassword, String rawPassword) {
        if (storedPassword == null) {
            return false;
        }
        if (storedPassword.startsWith("{noop}")) {
            return storedPassword.substring("{noop}".length()).equals(rawPassword);
        }
        return storedPassword.equals(rawPassword);
    }

    private void applyPermissions(Map<String, Object> user) {
        String roleName = (String) user.get("role_name");
        user.put("canCreateRent", "主管".equals(roleName));
        user.put("canEditRent", !"一般秘書".equals(roleName));
        user.put("canEditStaff", "主管".equals(roleName));
        user.put("canCreateOffice", true);
        user.put("canEditAllBranches", "主管".equals(roleName));
        user.put("canViewAllOffices", !"一般秘書".equals(roleName));
        user.put("canManageBranch", "主管".equals(roleName));
    }
}
