package com.example.cms.service;

import com.example.cms.dto.LoginRequest;
import com.example.cms.dto.PasswordResetConfirmationRequest;
import com.example.cms.dto.PasswordResetRequest;
import com.example.cms.dto.RegisterRequest;
import com.example.cms.service.support.CmsJdbcSupport;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;

@Service
public class AuthService extends CmsJdbcSupport {
    private static final String NOOP_PREFIX = "{noop}";
    private static final String PASSWORD_RESET_MESSAGE = "若帳號資料存在，重設說明已寄送至註冊信箱。";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final PasswordResetMailService passwordResetMailService;
    private final String passwordResetAppBaseUrl;
    private final int passwordResetTokenTtlMinutes;

    public AuthService(JdbcTemplate jdbc,
                       PasswordResetMailService passwordResetMailService,
                       @org.springframework.beans.factory.annotation.Value("${cms.password-reset.app-base-url}") String passwordResetAppBaseUrl,
                       @org.springframework.beans.factory.annotation.Value("${cms.password-reset.token-ttl-minutes}") int passwordResetTokenTtlMinutes) {
        super(jdbc);
        this.passwordResetMailService = passwordResetMailService;
        this.passwordResetAppBaseUrl = passwordResetAppBaseUrl;
        this.passwordResetTokenTtlMinutes = passwordResetTokenTtlMinutes;
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
            String storedHash = (String) user.get("password_hash");
            if (!passwordMatches(storedHash, request.password())) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid account or password");
            }
            if (storedHash != null && storedHash.startsWith(NOOP_PREFIX)) {
                upgradeToHashedPassword((Number) user.get("staff_id"), request.password());
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
                || request.email() == null || request.email().isBlank()
                || request.roleName() == null || request.roleName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "all fields are required");
        }
        String email = request.email().trim();
        if (!isEmail(email)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "email is invalid");
        }
        Integer duplicate = jdbc.queryForObject("SELECT COUNT(*) FROM staff WHERE account = ?",
                Integer.class, request.account().trim());
        if (duplicate != null && duplicate > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "account already exists");
        }
        Integer duplicateEmail = jdbc.queryForObject("SELECT COUNT(*) FROM staff WHERE LOWER(email) = LOWER(?)",
                Integer.class, email);
        if (duplicateEmail != null && duplicateEmail > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "email already exists");
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
                INSERT INTO staff (staff_id, role_permission_id, branch_id, staff_name, account, email, password_hash)
                VALUES (?, ?, 1, ?, ?, ?, ?)
                """, staffId, roleId, request.staffName().trim(), request.account().trim(),
                email, passwordEncoder.encode(request.password()));
        Map<String, Object> user = jdbc.queryForMap("""
                SELECT s.staff_id, s.staff_name, s.account, s.email, s.branch_id,
                       b.branch_name, r.role_permission_id, r.role_name, r.scope
                FROM staff s
                JOIN role_permissions r ON r.role_permission_id = s.role_permission_id
                LEFT JOIN branches b ON b.branch_id = s.branch_id
                WHERE s.staff_id = ?
                """, staffId);
        applyPermissions(user);
        return user;
    }

    @Transactional
    public Map<String, Object> requestPasswordReset(PasswordResetRequest request) {
        String identifier = request == null || request.identifier() == null ? "" : request.identifier().trim();
        if (identifier.isBlank()) {
            return Map.of("message", PASSWORD_RESET_MESSAGE);
        }
        try {
            Map<String, Object> user = jdbc.queryForMap("""
                    SELECT staff_id, email
                    FROM staff
                    WHERE LOWER(account) = LOWER(?) OR LOWER(email) = LOWER(?)
                    LIMIT 1
                    """, identifier, identifier);
            String email = (String) user.get("email");
            if (email == null || email.isBlank()) {
                return Map.of("message", PASSWORD_RESET_MESSAGE);
            }
            long staffId = ((Number) user.get("staff_id")).longValue();
            jdbc.update("UPDATE password_reset_tokens SET used_at = CURRENT_TIMESTAMP WHERE staff_id = ? AND used_at IS NULL", staffId);
            String rawToken = newPasswordResetToken();
            jdbc.update("""
                    INSERT INTO password_reset_tokens (staff_id, token_hash, expires_at)
                    VALUES (?, ?, ?)
                    """, staffId, sha256(rawToken), java.sql.Timestamp.from(Instant.now().plusSeconds(passwordResetTokenTtlMinutes * 60L)));
            try {
                passwordResetMailService.sendResetLink(email, resetLink(rawToken));
            } catch (RuntimeException ignored) {
                // The response must remain generic; the operator can diagnose configured SMTP separately.
            }
        } catch (EmptyResultDataAccessException ignored) {
            // Return the same status and message for an unknown account or email.
        }
        return Map.of("message", PASSWORD_RESET_MESSAGE);
    }

    @Transactional
    public void resetPassword(PasswordResetConfirmationRequest request) {
        if (request == null || request.token() == null || request.token().isBlank()
                || request.password() == null || request.password().length() < 8
                || !request.password().equals(request.confirmPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "token and matching password are required");
        }
        Map<String, Object> token;
        try {
            token = jdbc.queryForMap("""
                    SELECT p.password_reset_token_id, p.staff_id, s.email
                    FROM password_reset_tokens p
                    JOIN staff s ON s.staff_id = p.staff_id
                    WHERE p.token_hash = ?
                    """, sha256(request.token()));
        } catch (EmptyResultDataAccessException e) {
            throw invalidPasswordResetToken();
        }
        long tokenId = ((Number) token.get("password_reset_token_id")).longValue();
        int markedUsed = jdbc.update("""
                UPDATE password_reset_tokens
                SET used_at = CURRENT_TIMESTAMP
                WHERE password_reset_token_id = ?
                  AND used_at IS NULL
                  AND expires_at > CURRENT_TIMESTAMP
                """, tokenId);
        if (markedUsed != 1) {
            throw invalidPasswordResetToken();
        }
        long staffId = ((Number) token.get("staff_id")).longValue();
        jdbc.update("UPDATE staff SET password_hash = ? WHERE staff_id = ?", passwordEncoder.encode(request.password()), staffId);
        String email = (String) token.get("email");
        if (email != null && !email.isBlank()) {
            try {
                passwordResetMailService.sendPasswordChangedNotice(email);
            } catch (RuntimeException ignored) {
                // A delivery failure must not roll back a successfully secured password change.
            }
        }
    }

    private boolean passwordMatches(String storedPassword, String rawPassword) {
        if (storedPassword == null) {
            return false;
        }
        if (storedPassword.startsWith(NOOP_PREFIX)) {
            return storedPassword.substring(NOOP_PREFIX.length()).equals(rawPassword);
        }
        return passwordEncoder.matches(rawPassword, storedPassword);
    }

    private void upgradeToHashedPassword(Number staffId, String rawPassword) {
        jdbc.update("UPDATE staff SET password_hash = ? WHERE staff_id = ?",
                passwordEncoder.encode(rawPassword), staffId.longValue());
    }

    private String newPasswordResetToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String resetLink(String rawToken) {
        return UriComponentsBuilder.fromUriString(passwordResetAppBaseUrl)
                .replaceQueryParam("resetToken", rawToken)
                .build()
                .encode()
                .toUriString();
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private ResponseStatusException invalidPasswordResetToken() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "password reset token is invalid or expired");
    }

    private boolean isEmail(String email) {
        return email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
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
        user.put("canReviewRefund", "主管".equals(roleName));
    }
}
