package com.example.cms.dto;

public record StaffProfileChangeRequest(
        Long requestedByStaffId,
        String staffName,
        String email
) {
}
