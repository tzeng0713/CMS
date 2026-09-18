package com.example.cms.controller;

import com.example.cms.dto.BackupTriggerRequest;
import com.example.cms.service.BackupService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
