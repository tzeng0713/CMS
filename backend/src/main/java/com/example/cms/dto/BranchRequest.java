package com.example.cms.dto;

public record BranchRequest(
        String branchName,
        String branchCode,
        String branchAddress,
        String taxId,
        String bankAccount,
        String bankBranch,
        String bankAccountName) {}
