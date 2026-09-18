package com.example.cms.service;

public interface PasswordResetMailService {
    void sendResetLink(String recipientEmail, String resetLink);

    void sendPasswordChangedNotice(String recipientEmail);
}
