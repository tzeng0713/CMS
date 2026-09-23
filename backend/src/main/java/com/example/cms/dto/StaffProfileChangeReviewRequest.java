package com.example.cms.dto;

public record StaffProfileChangeReviewRequest(
        Long reviewedByStaffId,
        Boolean approve
) {
}
