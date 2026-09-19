package com.example.cms.service;

public interface PasswordResetMailService {
    void sendEmailVerificationLink(String recipientEmail, String verificationLink);

    void sendResetLink(String recipientEmail, String resetLink);

    void sendPasswordChangedNotice(String recipientEmail);
}
