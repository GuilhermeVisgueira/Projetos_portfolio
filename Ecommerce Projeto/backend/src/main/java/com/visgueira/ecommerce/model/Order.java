package com.visgueira.ecommerce.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@Document(collection = "orders")
public class Order {

    @Id
    private String id;

    private String userId;
    private String customerEmail;
    private String status; // "PENDING", "PAID", "CANCELED", "FAILED"

    private List<OrderItem> items;
    private Long subtotalCents = 0L;
    private String couponCode;
    private Long discountCents = 0L;
    private String shippingService;
    private Long shippingCents = 0L;
    private Long totalAmountCents;
    private String currency = "BRL";

    private String stripeSessionId;
    private String stripeCheckoutUrl;
    private Address shippingAddress;

    private Instant createdAt = Instant.now();
    private Instant paidAt;

    public Order() {
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getCustomerEmail() { return customerEmail; }
    public void setCustomerEmail(String customerEmail) { this.customerEmail = customerEmail; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public List<OrderItem> getItems() { return items; }
    public void setItems(List<OrderItem> items) { this.items = items; }

    public Long getSubtotalCents() { return subtotalCents; }
    public void setSubtotalCents(Long subtotalCents) { this.subtotalCents = subtotalCents; }

    public String getCouponCode() { return couponCode; }
    public void setCouponCode(String couponCode) { this.couponCode = couponCode; }

    public Long getDiscountCents() { return discountCents; }
    public void setDiscountCents(Long discountCents) { this.discountCents = discountCents; }

    public String getShippingService() { return shippingService; }
    public void setShippingService(String shippingService) { this.shippingService = shippingService; }

    public Long getShippingCents() { return shippingCents; }
    public void setShippingCents(Long shippingCents) { this.shippingCents = shippingCents; }

    public Long getTotalAmountCents() { return totalAmountCents; }
    public void setTotalAmountCents(Long totalAmountCents) { this.totalAmountCents = totalAmountCents; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getStripeSessionId() { return stripeSessionId; }
    public void setStripeSessionId(String stripeSessionId) { this.stripeSessionId = stripeSessionId; }

    public String getStripeCheckoutUrl() { return stripeCheckoutUrl; }
    public void setStripeCheckoutUrl(String stripeCheckoutUrl) { this.stripeCheckoutUrl = stripeCheckoutUrl; }

    public Address getShippingAddress() { return shippingAddress; }
    public void setShippingAddress(Address shippingAddress) { this.shippingAddress = shippingAddress; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getPaidAt() { return paidAt; }
    public void setPaidAt(Instant paidAt) { this.paidAt = paidAt; }
}
