package com.example.cms.controller;

import com.example.cms.dto.ContractRequest;
import com.example.cms.dto.ContractWithFirstPaymentRequest;
import com.example.cms.service.ContractService;
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
@RequestMapping("/api/contracts")
public class ContractController {
    private final ContractService service;

    public ContractController(ContractService service) {
        this.service = service;
    }

    @GetMapping
    public Map<String, Object> contracts(@RequestParam(required = false) String search,
                                         @RequestParam(required = false) String companyName,
                                         @RequestParam(required = false) String taxId,
                                         @RequestParam(required = false) String startDateText,
                                         @RequestParam(required = false) String endDateText,
                                         @RequestParam(required = false) String leaseStatus,
                                         @RequestParam(required = false) Integer page,
                                         @RequestParam(required = false) Integer pageSize) {
        return service.contracts(search, companyName, taxId, startDateText, endDateText, leaseStatus, page, pageSize);
    }

    @PostMapping
    public Map<String, Object> createContract(@RequestBody ContractRequest request) {
        return service.createContract(request);
    }

    @PostMapping("/with-first-payment")
    public Map<String, Object> createContractWithFirstPayment(@RequestBody ContractWithFirstPaymentRequest request) {
        return service.createContractWithFirstPayment(request);
    }

    @PutMapping("/{id}")
    public Map<String, Object> updateContract(@PathVariable long id, @RequestBody ContractRequest request) {
        return service.updateContract(id, request);
    }
}
