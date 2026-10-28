package com.example.cms.dto;

import java.util.List;

public record SalesTargetRequest(
        Long targetStaffId,
        Integer targetMonth,
        List<SalesTargetEntry> entries,
        Long staffId
) {
}
