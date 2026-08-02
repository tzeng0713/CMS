package com.example.cms.controller;

import com.example.cms.dto.RentPaymentRequest;
import com.example.cms.service.RentPaymentService;
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
@RequestMapping("/api/rent-payments")
public class RentPaymentController {
    private final RentPaymentService service;

    public RentPaymentController(RentPaymentService service) {
        this.service = service;
    }

    @GetMapping
    public List<Map<String, Object>> rentPayments(@RequestParam(required = false) String search) {
        return service.rentPayments(search);
    }

    @PostMapping
    public Map<String, Object> createRentPayment(@RequestBody RentPaymentRequest request) {
        return service.createRentPayment(request);
    }

    @PutMapping("/{id}")
    public Map<String, Object> updateRentPayment(@PathVariable long id, @RequestBody RentPaymentRequest request) {
        return service.updateRentPayment(id, request);
    }
}
