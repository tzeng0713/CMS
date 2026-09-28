package com.example.cms.controller;

import com.example.cms.dto.BackupTriggerRequest;
import com.example.cms.service.BackupService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/backups")
public class BackupController {
    private final BackupService service;

    public BackupController(BackupService service) {
        this.service = service;
    }

    @PostMapping("/trigger")
    public Map<String, Object> trigger(@RequestBody BackupTriggerRequest request) {
        return service.triggerBackup(request.staffId());
    }

    @GetMapping("/logs")
    public List<Map<String, Object>> logs() {
        return service.listLogs();
    }

    /**
     * 一次性設定用：管理者用自己的瀏覽器開啟這個網址，登入 Google 帳號同意授權，
     * 換取的 refresh token 要手動填進環境變數 GOOGLE_OAUTH_REFRESH_TOKEN 並重啟後端。
     */
    @GetMapping("/oauth/authorize")
    public void authorize(HttpServletResponse response) throws IOException {
        response.sendRedirect(service.buildAuthorizationUrl());
    }

    @GetMapping("/oauth/callback")
    public ResponseEntity<String> oauthCallback(@RequestParam String code) throws Exception {
        String refreshToken = service.exchangeCodeForRefreshToken(code);
        String html = "<html><head><meta charset=\"UTF-8\"></head><body style=\"font-family:sans-serif\">"
                + "<h3>Google 授權完成</h3>"
                + "<p>請把下面這組 refresh token 複製貼到環境變數 <code>GOOGLE_OAUTH_REFRESH_TOKEN</code>，設定後重新啟動後端：</p>"
                + "<pre style=\"background:#eee;padding:12px;white-space:pre-wrap;word-break:break-all\">" + refreshToken + "</pre>"
                + "</body></html>";
        return ResponseEntity.ok()
                .contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
                .body(html);
    }
}
