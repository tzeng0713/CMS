package com.example.cms.controller;

import com.example.cms.dto.ImportChargeListRequest;
import com.example.cms.dto.RefundCancelRequest;
import com.example.cms.dto.RefundRequest;
import com.example.cms.dto.RefundReviewRequest;
import com.example.cms.service.RefundService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/refunds")
public class RefundController {
    private final RefundService service;

    public RefundController(RefundService service) {
        this.service = service;
    }

    @GetMapping
    public Map<String, Object> refunds(@RequestParam(required = false) String companyName,
                                        @RequestParam(required = false) String taxId,
                                        @RequestParam(required = false) String dateFrom,
                                        @RequestParam(required = false) String dateTo,
                                        @RequestParam(required = false) String status,
                                        @RequestParam(required = false) Integer page,
                                        @RequestParam(required = false) Integer pageSize,
                                        @RequestParam(required = false) String sortBy,
                                        @RequestParam(required = false) String sortDir) {
        return service.refunds(companyName, taxId, dateFrom, dateTo, status, page, pageSize, sortBy, sortDir);
    }

    @GetMapping("/{id}")
    public Map<String, Object> refundDetail(@PathVariable long id) {
        return service.refundDetail(id);
    }

    @PostMapping
    public Map<String, Object> createRefund(@RequestBody RefundRequest request) {
        return service.createRefund(request);
    }

    @PutMapping("/{id}")
    public Map<String, Object> updateRefund(@PathVariable long id, @RequestBody RefundRequest request) {
        return service.updateRefund(id, request);
    }

    @PatchMapping("/{id}/cancel")
    public Map<String, Object> cancelRefund(@PathVariable long id, @RequestBody RefundCancelRequest request) {
        return service.cancelRefund(id, request);
    }

    @PatchMapping("/{id}/review")
    public Map<String, Object> reviewRefund(@PathVariable long id, @RequestBody RefundReviewRequest request) {
        return service.reviewRefund(id, request);
    }

    @PostMapping("/import-charge-list")
    public Map<String, Object> importChargeList(@RequestBody ImportChargeListRequest request) {
        return service.importChargeList(request);
    }
}
