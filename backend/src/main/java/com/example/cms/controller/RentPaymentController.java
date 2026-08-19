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

import java.util.Map;

@RestController
@RequestMapping("/api/rent-payments")
public class RentPaymentController {
    private final RentPaymentService service;

    public RentPaymentController(RentPaymentService service) {
        this.service = service;
    }

    @GetMapping
    public Map<String, Object> rentPayments(@RequestParam(required = false) String search,
                                            @RequestParam(required = false) String companyName,
                                            @RequestParam(required = false) String taxId,
                                            @RequestParam(required = false) String paymentDateStartText,
                                            @RequestParam(required = false) String paymentDateEndText,
                                            @RequestParam(required = false) Integer page,
                                            @RequestParam(required = false) Integer pageSize) {
        return service.rentPayments(search, companyName, taxId, paymentDateStartText, paymentDateEndText, page, pageSize);
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
