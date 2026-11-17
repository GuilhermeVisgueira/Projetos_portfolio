package com.visgueira.ecommerce.dto;

public record ShippingQuoteResponse(
    String zipCode,
    String region,
    String serviceType,
    long shippingCents,
    int estimatedDeliveryDays,
    boolean freeShippingApplied
) {}
