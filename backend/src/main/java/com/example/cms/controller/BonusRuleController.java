package com.example.cms.controller;

import com.example.cms.dto.BonusRuleRequest;
import com.example.cms.service.BonusRuleService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/bonus-rules")
public class BonusRuleController {
    private final BonusRuleService service;

    public BonusRuleController(BonusRuleService service) {
        this.service = service;
    }

    @GetMapping
    public List<Map<String, Object>> bonusRules() {
        return service.list();
    }

    @PostMapping
    public Map<String, Object> createBonusRule(@RequestBody BonusRuleRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public Map<String, Object> updateBonusRule(@PathVariable long id, @RequestBody BonusRuleRequest request) {
        return service.update(id, request);
    }
}
