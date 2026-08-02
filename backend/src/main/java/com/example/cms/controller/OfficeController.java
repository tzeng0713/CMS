package com.example.cms.controller;

import com.example.cms.dto.OfficeRequest;
import com.example.cms.service.OfficeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/offices")
public class OfficeController {
    private final OfficeService service;

    public OfficeController(OfficeService service) {
        this.service = service;
    }

    @GetMapping
    public List<Map<String, Object>> offices(@RequestParam(required = false) Long branchId) {
        return service.offices(branchId);
    }

    @PostMapping
    public Map<String, Object> createOffice(@RequestBody OfficeRequest request) {
        return service.createOffice(request);
    }

    @PutMapping("/{id}")
    public Map<String, Object> updateOffice(@PathVariable long id, @RequestBody OfficeRequest request) {
        return service.updateOffice(id, request);
    }
}
