package com.visgueira.ecommerce.model;

/**
 * Snapshot imutável de um item no momento da compra.
 */
public class OrderItem {

    private String productId;
    private String sku;
    private String name;
    private String sellerId;
    private Long unitPriceCents;
    private Integer quantity;

    public OrderItem() {
    }

    public OrderItem(String productId, String sku, String name, String sellerId, Long unitPriceCents, Integer quantity) {
        this.productId = productId;
        this.sku = sku;
        this.name = name;
        this.sellerId = sellerId;
        this.unitPriceCents = unitPriceCents;
        this.quantity = quantity;
    }

    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }

    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getSellerId() { return sellerId; }
    public void setSellerId(String sellerId) { this.sellerId = sellerId; }

    public Long getUnitPriceCents() { return unitPriceCents; }
    public void setUnitPriceCents(Long unitPriceCents) { this.unitPriceCents = unitPriceCents; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
}
