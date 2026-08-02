package com.example.cms.controller;

import com.example.cms.dto.ChargeListRequest;
import com.example.cms.service.ChargeListService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/charge-lists")
public class ChargeListController {
    private final ChargeListService service;

    public ChargeListController(ChargeListService service) {
        this.service = service;
    }

    @GetMapping
    public Map<String, Object> chargeLists(@RequestParam(required = false) Long chargeListId,
                                           @RequestParam(required = false) Long customerId,
                                           @RequestParam(required = false) Long contractId,
                                           @RequestParam(required = false) String feeStartMonth,
                                           @RequestParam(required = false) String feeEndMonth,
                                           @RequestParam(required = false) Integer status,
                                           @RequestParam(required = false) Long createdBy,
                                           @RequestParam(required = false) String issuedFrom,
                                           @RequestParam(required = false) String issuedTo,
                                           @RequestParam(required = false) Integer page,
                                           @RequestParam(required = false) Integer pageSize,
                                           @RequestParam(required = false) String sortBy,
                                           @RequestParam(required = false) String sortDir) {
        return service.chargeLists(chargeListId, customerId, contractId, feeStartMonth, feeEndMonth,
                status, createdBy, issuedFrom, issuedTo, page, pageSize, sortBy, sortDir);
    }

    @GetMapping("/{id}")
    public Map<String, Object> chargeListDetail(@PathVariable long id) {
        return service.chargeListDetail(id);
    }

    @PostMapping
    public Map<String, Object> createChargeList(@RequestBody ChargeListRequest request) {
        return service.createChargeList(request);
    }

    @PutMapping("/{id}")
    public Map<String, Object> updateChargeList(@PathVariable long id, @RequestBody ChargeListRequest request) {
        return service.updateChargeList(id, request);
    }
}
