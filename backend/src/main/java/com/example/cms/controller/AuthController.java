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
        return ResponseEntity.accepted().body(service.register(request));
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
