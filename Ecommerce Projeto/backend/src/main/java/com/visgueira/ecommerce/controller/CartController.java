package com.visgueira.ecommerce.controller;

import com.visgueira.ecommerce.dto.ShippingQuoteResponse;
import com.visgueira.ecommerce.model.Cart;
import com.visgueira.ecommerce.service.CartService;
import com.visgueira.ecommerce.service.ShippingService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class CartController {

    private final CartService cartService;
    private final ShippingService shippingService;

    public CartController(CartService cartService, ShippingService shippingService) {
        this.cartService = cartService;
        this.shippingService = shippingService;
    }

    /**
     * Simulador público de frete (PAC e SEDEX) por CEP, peso e subtotal.
     */
    @GetMapping("/shipping/quote")
    public ResponseEntity<List<ShippingQuoteResponse>> quoteShipping(
            @RequestParam String zipCode,
            @RequestParam(defaultValue = "500") int weightGrams,
            @RequestParam(defaultValue = "10000") long subtotalCents) {
        return ResponseEntity.ok(shippingService.quoteAllOptions(zipCode, weightGrams, subtotalCents));
    }

    @GetMapping("/cart")
    public ResponseEntity<Cart> getMyCart(Authentication authentication) {
        String userId = (String) authentication.getPrincipal();
        return ResponseEntity.ok(cartService.getOrCreateCart(userId));
    }

    @PostMapping("/cart/items")
    public ResponseEntity<Cart> addOrUpdateItem(
            @RequestBody Map<String, Object> body,
            Authentication authentication) {
        String userId = (String) authentication.getPrincipal();
        String sku = (String) body.get("sku");
        int quantity = body.get("quantity") instanceof Number n ? n.intValue() : 0;
        return ResponseEntity.ok(cartService.addOrUpdateItem(userId, sku, quantity));
    }

    @DeleteMapping("/cart/items/{sku}")
    public ResponseEntity<Cart> removeItem(
            @PathVariable String sku,
            Authentication authentication) {
        String userId = (String) authentication.getPrincipal();
        return ResponseEntity.ok(cartService.removeItem(userId, sku));
    }

    @PostMapping("/cart/coupon")
    public ResponseEntity<Cart> applyCoupon(
            @RequestBody Map<String, String> body,
            Authentication authentication) {
        String userId = (String) authentication.getPrincipal();
        String code = body.get("code");
        return ResponseEntity.ok(cartService.applyCoupon(userId, code));
    }

    @PostMapping("/cart/shipping")
    public ResponseEntity<Cart> calculateAndSelectShipping(
            @RequestBody Map<String, String> body,
            Authentication authentication) {
        String userId = (String) authentication.getPrincipal();
        String zipCode = body.get("zipCode");
        String serviceType = body.getOrDefault("serviceType", "PAC");
        return ResponseEntity.ok(cartService.applyShipping(userId, zipCode, serviceType));
    }

    @DeleteMapping("/cart")
    public ResponseEntity<Void> clearCart(Authentication authentication) {
        String userId = (String) authentication.getPrincipal();
        cartService.clearCart(userId);
        return ResponseEntity.noContent().build();
    }
}
