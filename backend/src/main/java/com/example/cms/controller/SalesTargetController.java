package com.example.cms.controller;

import com.example.cms.dto.SalesTargetRequest;
import com.example.cms.service.SalesTargetService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/sales-targets")
public class SalesTargetController {
    private final SalesTargetService service;

    public SalesTargetController(SalesTargetService service) {
        this.service = service;
    }

    @GetMapping
    public Map<String, Object> salesTargets(@RequestParam(required = false) Long branchId,
                                             @RequestParam(required = false) Integer targetMonth,
                                             @RequestParam(required = false) String category,
                                             @RequestParam(required = false) Integer page,
                                             @RequestParam(required = false) Integer pageSize) {
        return service.list(branchId, targetMonth, category, page, pageSize);
    }

    @PostMapping
    public Map<String, Object> createSalesTarget(@RequestBody SalesTargetRequest request) {
        return service.create(request);
    }
}
