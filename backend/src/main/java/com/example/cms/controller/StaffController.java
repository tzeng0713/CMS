package com.example.cms.controller;

import com.example.cms.dto.StaffUpdateRequest;
import com.example.cms.dto.StaffApprovalRequest;
import com.example.cms.dto.StaffProfileChangeRequest;
import com.example.cms.dto.StaffProfileChangeReviewRequest;
import com.example.cms.service.StaffService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
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

    @PatchMapping("/{id}/approval")
    public Map<String, Object> approveStaff(@PathVariable long id, @RequestBody StaffApprovalRequest request) {
        return service.approveStaff(id, request.approvedByStaffId());
    }

    @PostMapping("/{id}/profile-change-requests")
    public org.springframework.http.ResponseEntity<Map<String, Object>> requestProfileChange(
            @PathVariable long id, @RequestBody StaffProfileChangeRequest request) {
        return org.springframework.http.ResponseEntity.accepted().body(service.requestProfileChange(id, request));
    }

    @GetMapping("/profile-change-requests")
    public List<Map<String, Object>> profileChangeRequests(
            @RequestParam(required = false) Long staffId,
            @RequestParam(defaultValue = "false") boolean pendingOnly) {
        return service.profileChangeRequests(staffId, pendingOnly);
    }

    @PatchMapping("/profile-change-requests/{requestId}")
    public Map<String, Object> reviewProfileChange(
            @PathVariable long requestId, @RequestBody StaffProfileChangeReviewRequest request) {
        return service.reviewProfileChange(requestId, request);
    }
}
