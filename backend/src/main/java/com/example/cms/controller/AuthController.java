package com.example.cms.controller;

import com.example.cms.dto.LoginRequest;
import com.example.cms.dto.EmailVerificationConfirmationRequest;
import com.example.cms.dto.EmailVerificationRequest;
import com.example.cms.dto.PasswordResetConfirmationRequest;
import com.example.cms.dto.PasswordResetRequest;
import com.example.cms.dto.RegisterRequest;
import com.example.cms.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService service;

    public AuthController(AuthService service) {
        this.service = service;
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody LoginRequest request) {
        return service.login(request);
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@RequestBody RegisterRequest request) {
        try {
            return ResponseEntity.accepted().body(service.register(request));
        } catch (ResponseStatusException exception) {
            Map<String, String> fieldErrors = registrationFieldErrors(request, exception.getReason());
            if (!fieldErrors.isEmpty()) {
                return ResponseEntity.status(exception.getStatusCode()).body(Map.of("fieldErrors", fieldErrors));
            }
            throw exception;
        }
    }

    private Map<String, String> registrationFieldErrors(RegisterRequest request, String reason) {
        if ("all fields are required".equals(reason)) {
            Map<String, String> errors = new LinkedHashMap<>();
            if (isBlank(request.staffName())) errors.put("staffName", "請輸入職員名稱。");
            if (isBlank(request.account())) errors.put("account", "請輸入帳號。");
            if (isBlank(request.password())) errors.put("password", "請輸入密碼。");
            if (isBlank(request.email())) errors.put("email", "請輸入信箱。");
            return errors;
        }
        return switch (reason == null ? "" : reason) {
            case "account already exists" -> Map.of("account", "此帳號已被使用，請改用其他帳號。");
            case "email already exists" -> Map.of("email", "此信箱已被使用，請改用其他信箱。");
            case "email is invalid" -> Map.of("email", "請輸入有效的信箱格式。");
            case "password must be at least 8 characters" -> Map.of("password", "密碼至少需要 8 個字元。");
            default -> Map.of();
        };
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    @PostMapping("/email-verification-requests")
    public ResponseEntity<Map<String, Object>> requestEmailVerification(@RequestBody EmailVerificationRequest request) {
        return ResponseEntity.accepted().body(service.requestEmailVerification(request));
    }

    @PostMapping("/email-verifications")
    public ResponseEntity<Void> verifyEmail(@RequestBody EmailVerificationConfirmationRequest request) {
        service.verifyEmail(request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/password-reset-requests")
    public ResponseEntity<Map<String, Object>> requestPasswordReset(@RequestBody PasswordResetRequest request) {
        return ResponseEntity.accepted().body(service.requestPasswordReset(request));
    }

    @PostMapping("/password-resets")
    public ResponseEntity<Void> resetPassword(@RequestBody PasswordResetConfirmationRequest request) {
        service.resetPassword(request);
        return ResponseEntity.noContent().build();
    }
}
