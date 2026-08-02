package com.example.cms.controller;

import com.example.cms.dto.BranchRequest;
import com.example.cms.service.BranchService;
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
@RequestMapping("/api/branches")
public class BranchController {
    private final BranchService service;

    public BranchController(BranchService service) {
        this.service = service;
    }

    @GetMapping
    public List<Map<String, Object>> branches() {
        return service.branches();
    }

    @PostMapping
    public Map<String, Object> createBranch(@RequestBody BranchRequest request) {
        return service.createBranch(request);
    }

    @PutMapping("/{id}")
    public Map<String, Object> updateBranch(@PathVariable long id, @RequestBody BranchRequest request) {
        return service.updateBranch(id, request);
    }
}
