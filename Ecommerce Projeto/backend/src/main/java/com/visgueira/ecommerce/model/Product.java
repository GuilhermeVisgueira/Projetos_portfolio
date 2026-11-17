package com.visgueira.ecommerce.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

@Document(collection = "products")
public class Product {

    @Id
    private String id;

    @Indexed(name = "idx_products_sku_unique", unique = true)
    private String sku;

    private String name;
    private String description;
    private Long priceCents;
    private Integer weightGrams = 500;
    private String currency = "BRL";
    private Integer stock;
    private String category;

    // Identificação do Vendedor (SELLER) responsável pelo produto
    private String sellerId;
    private String sellerStoreName;

    /**
     * Atributos polimórficos/variados conforme a categoria do produto
     * (ex: RAM/SSD para notebooks, tamanho/cor para roupas, ISBN/autor para livros).
     */
    private Map<String, Object> attributes;

    private Boolean active = true;
    private Instant createdAt = Instant.now();

    public Product() {
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Long getPriceCents() { return priceCents; }
    public void setPriceCents(Long priceCents) { this.priceCents = priceCents; }

    public Integer getWeightGrams() { return weightGrams != null && weightGrams > 0 ? weightGrams : 500; }
    public void setWeightGrams(Integer weightGrams) { this.weightGrams = weightGrams; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public Integer getStock() { return stock; }
    public void setStock(Integer stock) { this.stock = stock; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getSellerId() { return sellerId; }
    public void setSellerId(String sellerId) { this.sellerId = sellerId; }

    public String getSellerStoreName() { return sellerStoreName; }
    public void setSellerStoreName(String sellerStoreName) { this.sellerStoreName = sellerStoreName; }

    public Map<String, Object> getAttributes() { return attributes; }
    public void setAttributes(Map<String, Object> attributes) { this.attributes = attributes; }

    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
