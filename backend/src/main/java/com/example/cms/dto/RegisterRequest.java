package com.example.cms.dto;

public record RegisterRequest(
        String staffName,
        String account,
        String password,
        String roleName
) {
}
