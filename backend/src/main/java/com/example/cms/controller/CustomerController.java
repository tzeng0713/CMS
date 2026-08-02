package com.example.cms.controller;

import com.example.cms.dto.CustomerRequest;
import com.example.cms.dto.CustomerWithContractRequest;
import com.example.cms.service.ContractService;
import com.example.cms.service.CustomerOnboardingService;
import com.example.cms.service.CustomerService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {
    private final CustomerService service;
    private final ContractService contractService;
    private final CustomerOnboardingService onboardingService;
    private final ObjectMapper objectMapper;

    public CustomerController(CustomerService service, ContractService contractService,
                              CustomerOnboardingService onboardingService, ObjectMapper objectMapper) {
        this.service = service;
        this.contractService = contractService;
        this.onboardingService = onboardingService;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public List<Map<String, Object>> customers(@RequestParam(required = false) String search,
                                               @RequestParam(required = false) String ownerName,
                                               @RequestParam(required = false) String companyName,
                                               @RequestParam(required = false) String taxId,
                                               @RequestParam(required = false) String phone,
                                               @RequestParam(required = false) Long branchId,
                                               @RequestParam(required = false) String officeNo,
                                               @RequestParam(required = false) Integer ownerBirthdayMonth,
                                               @RequestParam(required = false) Integer contactBirthdayMonth) {
        return service.customers(search, ownerName, companyName, taxId, phone, branchId, officeNo,
                ownerBirthdayMonth, contactBirthdayMonth);
    }

    @GetMapping("/{id}")
    public Map<String, Object> customerDetail(@PathVariable long id) {
        return service.customerDetail(id);
    }

    @GetMapping("/lookup")
    public List<Map<String, Object>> customerLookup(@RequestParam(required = false) String search) {
        return service.customerLookup(search);
    }

    @PostMapping
    public Map<String, Object> createCustomer(@RequestBody CustomerRequest request) {
        return service.createCustomer(request);
    }

    @PostMapping(value = "/with-contract", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> createCustomerWithContract(@RequestPart("payload") String payload,
                                                          @RequestPart(value = "leaseImage", required = false) MultipartFile leaseImage) {
        try {
            CustomerWithContractRequest request = objectMapper.readValue(payload, CustomerWithContractRequest.class);
            return onboardingService.createCustomerWithContract(request, leaseImage);
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid payload", e);
        }
    }

    @PutMapping("/{id}")
    public Map<String, Object> updateCustomer(@PathVariable long id, @RequestBody CustomerRequest request) {
        return service.updateCustomer(id, request);
    }

    @GetMapping("/{id}/same-owner")
    public List<Map<String, Object>> sameOwner(@PathVariable long id) {
        Map<String, Object> detail = service.customerDetail(id);
        return service.sameOwnerCompanies((String) detail.get("owner_name"), id);
    }

    @GetMapping("/{id}/latest-contract")
    public Map<String, Object> latestContract(@PathVariable long id) {
        return contractService.latestContract(id);
    }
}
