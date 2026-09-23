package com.example.cms.dto;

public record PasswordResetConfirmationRequest(
        String token,
        String password,
        String confirmPassword
) {
}
