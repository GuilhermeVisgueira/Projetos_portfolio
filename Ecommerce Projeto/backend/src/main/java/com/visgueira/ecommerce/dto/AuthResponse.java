package com.visgueira.ecommerce.dto;

public record AuthResponse(
    String accessToken,
    String tokenType,
    long expiresInMs,
    UserResponse user
) {}
