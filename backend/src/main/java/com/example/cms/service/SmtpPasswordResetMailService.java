package com.example.cms.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class SmtpPasswordResetMailService implements PasswordResetMailService {
    private static final Logger log = LoggerFactory.getLogger(SmtpPasswordResetMailService.class);

    private final ObjectProvider<JavaMailSender> mailSender;
    private final String from;

    public SmtpPasswordResetMailService(ObjectProvider<JavaMailSender> mailSender,
                                        @Value("${cms.password-reset.from}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public void sendResetLink(String recipientEmail, String resetLink) {
        send(recipientEmail, "CMS 密碼重設", """
                我們收到您的 CMS 密碼重設申請。

                請在 30 分鐘內開啟以下連結設定新密碼：
                %s

                若不是您本人提出申請，請忽略此信件。
                """.formatted(resetLink));
    }

    @Override
    public void sendPasswordChangedNotice(String recipientEmail) {
        send(recipientEmail, "CMS 密碼已變更", "您的 CMS 密碼已成功變更。若非您本人操作，請立即聯絡系統管理員。");
    }

    private void send(String recipientEmail, String subject, String text) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            log.warn("Password reset mail was not delivered because SMTP is not configured.");
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(recipientEmail);
        message.setSubject(subject);
        message.setText(text);
        sender.send(message);
    }
}
