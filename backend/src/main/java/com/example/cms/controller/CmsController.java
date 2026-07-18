package com.example.cms.controller;

import com.example.cms.dto.CustomerRequest;
import com.example.cms.dto.CustomerWithContractRequest;
import com.example.cms.dto.ContractRequest;
import com.example.cms.dto.LoginRequest;
import com.example.cms.dto.RegisterRequest;
import com.example.cms.dto.RentPaymentRequest;
import com.example.cms.dto.StaffUpdateRequest;
import com.example.cms.service.CmsQueryService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class CmsController {
    private final CmsQueryService service;
    private final ObjectMapper objectMapper;

    public CmsController(CmsQueryService service, ObjectMapper objectMapper) {
        this.service = service;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/dashboard")
    public Map<String, Object> dashboard() {
        return service.dashboard();
    }

    @PostMapping("/auth/login")
    public Map<String, Object> login(@RequestBody LoginRequest request) {
        return service.login(request);
    }

    @PostMapping("/auth/register")
    public Map<String, Object> register(@RequestBody RegisterRequest request) {
        return service.register(request);
    }

    @GetMapping("/customers")
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

    @GetMapping("/customers/{id}")
    public Map<String, Object> customerDetail(@PathVariable long id) {
        return service.customerDetail(id);
    }

    @GetMapping("/customers/lookup")
    public List<Map<String, Object>> customerLookup(@RequestParam(required = false) String search) {
        return service.customerLookup(search);
    }

    @PostMapping("/customers")
    public Map<String, Object> createCustomer(@RequestBody CustomerRequest request) {
        return service.createCustomer(request);
    }

    @PostMapping(value = "/customers/with-contract", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> createCustomerWithContract(@RequestPart("payload") String payload,
                                                          @RequestPart(value = "leaseImage", required = false) MultipartFile leaseImage) {
        try {
            CustomerWithContractRequest request = objectMapper.readValue(payload, CustomerWithContractRequest.class);
            return service.createCustomerWithContract(request, leaseImage);
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid payload", e);
        }
    }

    @PutMapping("/customers/{id}")
    public Map<String, Object> updateCustomer(@PathVariable long id, @RequestBody CustomerRequest request) {
        return service.updateCustomer(id, request);
    }

    @GetMapping("/customers/{id}/same-owner")
    public List<Map<String, Object>> sameOwner(@PathVariable long id) {
        Map<String, Object> detail = service.customerDetail(id);
        return service.sameOwnerCompanies((String) detail.get("owner_name"), id);
    }

    @GetMapping("/customers/{id}/latest-contract")
    public Map<String, Object> latestContract(@PathVariable long id) {
        return service.latestContract(id);
    }

    @GetMapping("/offices")
    public List<Map<String, Object>> offices() {
        return service.offices();
    }

    @GetMapping("/contracts")
    public List<Map<String, Object>> contracts(@RequestParam(required = false) String search,
                                               @RequestParam(required = false) String companyName,
                                               @RequestParam(required = false) String startDateText,
                                               @RequestParam(required = false) String endDateText,
                                               @RequestParam(required = false) String leaseStatus) {
        return service.contracts(search, companyName, startDateText, endDateText, leaseStatus);
    }

    @PostMapping("/contracts")
    public Map<String, Object> createContract(@RequestBody ContractRequest request) {
        return service.createContract(request);
    }

    @PutMapping("/contracts/{id}")
    public Map<String, Object> updateContract(@PathVariable long id, @RequestBody ContractRequest request) {
        return service.updateContract(id, request);
    }

    @GetMapping("/staff")
    public List<Map<String, Object>> staff(@RequestParam(required = false) Long branchId) {
        return service.staff(branchId);
    }

    @PutMapping("/staff/{id}")
    public Map<String, Object> updateStaff(@PathVariable long id, @RequestBody StaffUpdateRequest request) {
        return service.updateStaff(id, request);
    }

    @GetMapping("/rent-payments")
    public List<Map<String, Object>> rentPayments(@RequestParam(required = false) String search) {
        return service.rentPayments(search);
    }

    @PostMapping("/rent-payments")
    public Map<String, Object> createRentPayment(@RequestBody RentPaymentRequest request) {
        return service.createRentPayment(request);
    }

    @PutMapping("/rent-payments/{id}")
    public Map<String, Object> updateRentPayment(@PathVariable long id, @RequestBody RentPaymentRequest request) {
        return service.updateRentPayment(id, request);
    }

    @GetMapping("/charge-lists")
    public List<Map<String, Object>> chargeLists() {
        return service.chargeLists();
    }

    @GetMapping("/refunds")
    public List<Map<String, Object>> refunds() {
        return service.refunds();
    }

    @GetMapping("/metadata")
    public Map<String, Object> metadata() {
        return service.metadata();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> badRequest(IllegalArgumentException e) {
        return Map.of("error", e.getMessage());
    }
}
