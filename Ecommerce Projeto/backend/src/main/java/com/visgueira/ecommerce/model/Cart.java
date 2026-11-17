package com.visgueira.ecommerce.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "carts")
public class Cart {

    @Id
    private String id;

    @Indexed(name = "idx_carts_user_unique", unique = true)
    private String userId;

    private List<CartItem> items = new ArrayList<>();

    private String couponCode;
    private Long discountCents = 0L;

    private String zipCode;
    private String shippingService; // "PAC" ou "SEDEX"
    private Long shippingCents = 0L;
    private Integer estimatedDeliveryDays;

    private Long subtotalCents = 0L;
    private Long totalCents = 0L;

    private Instant updatedAt = Instant.now();

    public Cart() {
    }

    public Cart(String userId) {
        this.userId = userId;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public List<CartItem> getItems() { return items; }
    public void setItems(List<CartItem> items) { this.items = items; }

    public String getCouponCode() { return couponCode; }
    public void setCouponCode(String couponCode) { this.couponCode = couponCode; }

    public Long getDiscountCents() { return discountCents; }
    public void setDiscountCents(Long discountCents) { this.discountCents = discountCents; }

    public String getZipCode() { return zipCode; }
    public void setZipCode(String zipCode) { this.zipCode = zipCode; }

    public String getShippingService() { return shippingService; }
    public void setShippingService(String shippingService) { this.shippingService = shippingService; }

    public Long getShippingCents() { return shippingCents; }
    public void setShippingCents(Long shippingCents) { this.shippingCents = shippingCents; }

    public Integer getEstimatedDeliveryDays() { return estimatedDeliveryDays; }
    public void setEstimatedDeliveryDays(Integer estimatedDeliveryDays) { this.estimatedDeliveryDays = estimatedDeliveryDays; }

    public Long getSubtotalCents() { return subtotalCents; }
    public void setSubtotalCents(Long subtotalCents) { this.subtotalCents = subtotalCents; }

    public Long getTotalCents() { return totalCents; }
    public void setTotalCents(Long totalCents) { this.totalCents = totalCents; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
