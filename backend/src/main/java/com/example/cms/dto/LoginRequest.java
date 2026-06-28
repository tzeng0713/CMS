package com.example.cms.dto;

public record LoginRequest(
        String account,
        String password
) {
}
