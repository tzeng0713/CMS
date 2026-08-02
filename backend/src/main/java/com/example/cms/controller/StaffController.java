package com.example.cms.controller;

import com.example.cms.dto.StaffUpdateRequest;
import com.example.cms.service.StaffService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/staff")
public class StaffController {
    private final StaffService service;

    public StaffController(StaffService service) {
        this.service = service;
    }

    @GetMapping
    public List<Map<String, Object>> staff(@RequestParam(required = false) Long branchId) {
        return service.staff(branchId);
    }

    @PutMapping("/{id}")
    public Map<String, Object> updateStaff(@PathVariable long id, @RequestBody StaffUpdateRequest request) {
        return service.updateStaff(id, request);
    }
}
