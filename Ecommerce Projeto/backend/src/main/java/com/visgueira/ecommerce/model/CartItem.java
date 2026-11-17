package com.visgueira.ecommerce.model;

public class CartItem {

    private String productId;
    private String sku;
    private String name;
    private String sellerId;
    private Long unitPriceCents;
    private Integer weightGrams;
    private Integer quantity;

    public CartItem() {
    }

    public CartItem(String productId, String sku, String name, String sellerId,
                    Long unitPriceCents, Integer weightGrams, Integer quantity) {
        this.productId = productId;
        this.sku = sku;
        this.name = name;
        this.sellerId = sellerId;
        this.unitPriceCents = unitPriceCents;
        this.weightGrams = weightGrams;
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

    public Integer getWeightGrams() { return weightGrams != null && weightGrams > 0 ? weightGrams : 500; }
    public void setWeightGrams(Integer weightGrams) { this.weightGrams = weightGrams; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
}
