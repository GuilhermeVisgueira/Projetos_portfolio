package com.visgueira.ecommerce.controller;

import com.visgueira.ecommerce.model.Product;
import com.visgueira.ecommerce.model.User;
import com.visgueira.ecommerce.repository.ProductRepository;
import com.visgueira.ecommerce.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    public ProductController(ProductRepository productRepository, UserRepository userRepository) {
        this.productRepository = productRepository;
        this.userRepository = userRepository;
    }

    @GetMapping
    public ResponseEntity<List<Product>> listProducts(@RequestParam(required = false) String category) {
        if (category != null && !category.isBlank()) {
            return ResponseEntity.ok(productRepository.findByCategoryIgnoreCaseAndActiveTrue(category));
        }
        return ResponseEntity.ok(productRepository.findByActiveTrue());
    }

    @GetMapping("/{sku}")
    public ResponseEntity<Product> getBySku(@PathVariable String sku) {
        return productRepository.findBySku(sku)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Rota exclusiva para Vendedores (SELLER) e Administradores (ADMIN) cadastrarem produtos variados.
     */
    @PostMapping
    public ResponseEntity<Product> createProduct(
            @RequestBody Product product,
            Authentication authentication) {

        if (productRepository.findBySku(product.getSku()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe um produto cadastrado com o SKU: " + product.getSku());
        }

        String sellerId = (String) authentication.getPrincipal();
        User seller = userRepository.findById(sellerId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vendedor não encontrado."));

        product.setSellerId(seller.getId());
        product.setSellerStoreName(seller.getStoreName() != null ? seller.getStoreName() : seller.getName());
        product.setActive(true);
        product.setCreatedAt(Instant.now());

        return ResponseEntity.status(HttpStatus.CREATED).body(productRepository.save(product));
    }
}
