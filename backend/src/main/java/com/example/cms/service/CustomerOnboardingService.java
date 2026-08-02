package com.example.cms.service;

import com.example.cms.dto.ContractRequest;
import com.example.cms.dto.CustomerWithContractRequest;
import com.example.cms.dto.RentPaymentRequest;
import com.example.cms.service.support.CmsJdbcSupport;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.Map;

@Service
public class CustomerOnboardingService extends CmsJdbcSupport {

    private final CustomerService customerService;
    private final ContractService contractService;
    private final RentPaymentService rentPaymentService;

    public CustomerOnboardingService(JdbcTemplate jdbc, CustomerService customerService,
                                     ContractService contractService, RentPaymentService rentPaymentService) {
        super(jdbc);
        this.customerService = customerService;
        this.contractService = contractService;
        this.rentPaymentService = rentPaymentService;
    }

    @Transactional
    public Map<String, Object> createCustomerWithContract(CustomerWithContractRequest request, MultipartFile leaseImage) {
        if (request == null || request.customer() == null || request.contract() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "customer and contract are required");
        }
        validateFirstPayment(request);
        Map<String, Object> customer = customerService.createCustomer(request.customer());
        Long customerId = ((Number) customer.get("customer_id")).longValue();
        ContractRequest contract = request.contract();
        Map<String, Object> createdContract = contractService.createContract(new ContractRequest(
                customerId,
                contract.officeId(),
                contract.rentalItem(),
                contract.rentalStatus(),
                contract.signedDateText(),
                contract.signerStaffId(),
                contract.partnerStaffId(),
                contract.sourceText(),
                contract.paymentMonths(),
                contract.startDateText(),
                contract.endDateText(),
                contract.terminationDateText(),
                contract.rent(),
                contract.deposit(),
                contract.leaseImagePath(),
                contract.leaseStatus(),
                contract.updatedBy() == null ? request.customer().updatedBy() : contract.updatedBy()));
        Long contractId = ((Number) createdContract.get("contract_id")).longValue();
        if (request.firstPaymentAmount() != null) {
            Long updatedBy = contract.updatedBy() == null ? request.customer().updatedBy() : contract.updatedBy();
            LocalDate feeStartDate = localDate(contract.startDateText());
            LocalDate feeEndDate = feeStartDate.plusMonths(contract.paymentMonths()).minusDays(1);
            LocalDate contractEndDate = localDate(contract.endDateText());
            if (contractEndDate != null && feeEndDate.isAfter(contractEndDate)) {
                feeEndDate = contractEndDate;
            }
            rentPaymentService.createRentPayment(new RentPaymentRequest(
                    customerId,
                    contractId,
                    paymentMonth(request.firstPaymentDateText()),
                    request.firstPaymentDateText(),
                    feeStartDate.toString(),
                    feeEndDate.toString(),
                    request.firstPaymentAmount(),
                    null,
                    "首次繳款",
                    updatedBy));
        }
        if (leaseImage != null && !leaseImage.isEmpty()) {
            String imagePath = contractService.saveLeaseImage(contractId, leaseImage);
            contractService.updateLeaseImagePath(contractId, imagePath);
        }
        return customerService.customerDetail(customerId);
    }

    private void validateFirstPayment(CustomerWithContractRequest request) {
        boolean hasAmount = request.firstPaymentAmount() != null;
        boolean hasDate = blankToNull(request.firstPaymentDateText()) != null;
        if (hasAmount != hasDate) {
            throw new IllegalArgumentException("firstPaymentAmount and firstPaymentDateText must be provided together");
        }
        if (hasAmount && request.firstPaymentAmount().signum() <= 0) {
            throw new IllegalArgumentException("firstPaymentAmount must be greater than zero");
        }
        if (hasDate && localDate(request.firstPaymentDateText()) == null) {
            throw new IllegalArgumentException("firstPaymentDateText must be a valid date");
        }
        if (hasAmount) {
            ContractRequest contract = request.contract();
            LocalDate contractStart = localDate(contract.startDateText());
            LocalDate contractEnd = localDate(contract.endDateText());
            if (contractStart == null) {
                throw new IllegalArgumentException("contract startDateText is required for the first payment");
            }
            if (contractEnd != null && contractEnd.isBefore(contractStart)) {
                throw new IllegalArgumentException("contract endDateText must not be before startDateText");
            }
            if (contract.paymentMonths() == null || contract.paymentMonths() <= 0) {
                throw new IllegalArgumentException("contract paymentMonths is required for the first payment");
            }
        }
    }

    private Integer paymentMonth(String dateText) {
        LocalDate date = localDate(dateText);
        return date == null ? null : date.getYear() * 100 + date.getMonthValue();
    }
}
