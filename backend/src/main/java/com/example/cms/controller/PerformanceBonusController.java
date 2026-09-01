package com.example.cms.controller;

import com.example.cms.dto.ManualPerformanceBonusRequest;
import com.example.cms.dto.SettleMonthlyBonusRequest;
import com.example.cms.dto.SettlePeriodBonusRequest;
import com.example.cms.dto.SyncTransactionBonusRequest;
import com.example.cms.service.PerformanceBonusService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/performance-bonuses")
public class PerformanceBonusController {
    private final PerformanceBonusService service;

    public PerformanceBonusController(PerformanceBonusService service) {
        this.service = service;
    }

    @GetMapping
    public Map<String, Object> performanceBonuses(@RequestParam(required = false) String ruleType,
                                                    @RequestParam(required = false) String excludeRuleType,
                                                    @RequestParam(required = false) String period,
                                                    @RequestParam(required = false) Long branchId,
                                                    @RequestParam(required = false) Long staffId,
                                                    @RequestParam(required = false) Long contractId,
                                                    @RequestParam(required = false) Integer page,
                                                    @RequestParam(required = false) Integer pageSize) {
        return service.list(ruleType, excludeRuleType, period, branchId, staffId, contractId, page, pageSize);
    }

    @PostMapping("/sync-transactions")
    public Map<String, Object> syncTransactions(@RequestBody SyncTransactionBonusRequest request) {
        return service.syncTransactionBonuses(request);
    }

    @PostMapping("/settle-monthly")
    public Map<String, Object> settleMonthly(@RequestBody SettleMonthlyBonusRequest request) {
        return service.settleMonthly(request);
    }

    @PostMapping("/settle-period")
    public Map<String, Object> settlePeriod(@RequestBody SettlePeriodBonusRequest request) {
        return service.settlePeriod(request);
    }

    @PostMapping("/manual")
    public Map<String, Object> manualCreate(@RequestBody ManualPerformanceBonusRequest request) {
        return service.manualCreate(request);
    }
}
