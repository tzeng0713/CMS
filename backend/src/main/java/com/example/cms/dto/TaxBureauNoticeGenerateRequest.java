package com.example.cms.dto;

import java.util.List;

public record TaxBureauNoticeGenerateRequest(
        String yearMonth,
        List<TaxBureauNoticeSelection> items,
        List<TaxBureauNoticeBranchInfo> branchInfo
) {
}
