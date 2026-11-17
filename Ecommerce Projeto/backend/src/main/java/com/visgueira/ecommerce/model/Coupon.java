package com.visgueira.ecommerce.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "coupons")
public class Coupon {

    @Id
    private String id;

    @Indexed(name = "idx_coupons_code_unique", unique = true)
    private String code;

    private Integer discountPercentage; // Ex: 10 = 10%
    private Long maxDiscountCents;      // Limite máximo de desconto em centavos (opcional)
    private Long minOrderCents;         // Valor mínimo de subtotal para aplicar (opcional)
    private Instant expiresAt;
    private Boolean active = true;

    public Coupon() {
    }

    public Coupon(String code, Integer discountPercentage, Long maxDiscountCents, Long minOrderCents, Instant expiresAt, Boolean active) {
        this.code = code;
        this.discountPercentage = discountPercentage;
        this.maxDiscountCents = maxDiscountCents;
        this.minOrderCents = minOrderCents;
        this.expiresAt = expiresAt;
        this.active = active;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public Integer getDiscountPercentage() { return discountPercentage; }
    public void setDiscountPercentage(Integer discountPercentage) { this.discountPercentage = discountPercentage; }

    public Long getMaxDiscountCents() { return maxDiscountCents; }
    public void setMaxDiscountCents(Long maxDiscountCents) { this.maxDiscountCents = maxDiscountCents; }

    public Long getMinOrderCents() { return minOrderCents; }
    public void setMinOrderCents(Long minOrderCents) { this.minOrderCents = minOrderCents; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
}
