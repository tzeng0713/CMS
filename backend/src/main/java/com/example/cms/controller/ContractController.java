package com.example.cms.controller;

import com.example.cms.dto.ContractRequest;
import com.example.cms.service.ContractService;
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
@RequestMapping("/api/contracts")
public class ContractController {
    private final ContractService service;

    public ContractController(ContractService service) {
        this.service = service;
    }

    @GetMapping
    public List<Map<String, Object>> contracts(@RequestParam(required = false) String search,
                                               @RequestParam(required = false) String companyName,
                                               @RequestParam(required = false) String startDateText,
                                               @RequestParam(required = false) String endDateText,
                                               @RequestParam(required = false) String leaseStatus) {
        return service.contracts(search, companyName, startDateText, endDateText, leaseStatus);
    }

    @PostMapping
    public Map<String, Object> createContract(@RequestBody ContractRequest request) {
        return service.createContract(request);
    }

    @PutMapping("/{id}")
    public Map<String, Object> updateContract(@PathVariable long id, @RequestBody ContractRequest request) {
        return service.updateContract(id, request);
    }
}
