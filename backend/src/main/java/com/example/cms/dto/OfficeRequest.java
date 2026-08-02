package com.example.cms.dto;

import java.util.List;

public record OfficeRequest(
        String officeNo,
        Long branchId,
        String notes,
        List<OfficeContactRequest> contacts
) {
}
