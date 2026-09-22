package com.example.cms.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> badRequest(IllegalArgumentException e) {
        return Map.of("error", e.getMessage());
    }

    @ExceptionHandler(RefundOverDeductedException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> refundOverDeducted(RefundOverDeductedException e) {
        Map<String, Object> body = new HashMap<>();
        body.put("error", e.getMessage());
        body.put("chargeListId", e.getChargeListId());
        body.put("customerId", e.getCustomerId());
        body.put("contractId", e.getContractId());
        return body;
    }
}
