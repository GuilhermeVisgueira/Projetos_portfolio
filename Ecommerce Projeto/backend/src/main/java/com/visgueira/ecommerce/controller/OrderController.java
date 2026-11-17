package com.visgueira.ecommerce.controller;

import com.visgueira.ecommerce.dto.CheckoutRequest;
import com.visgueira.ecommerce.model.Order;
import com.visgueira.ecommerce.repository.OrderRepository;
import com.visgueira.ecommerce.service.StripePaymentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final StripePaymentService stripePaymentService;
    private final OrderRepository orderRepository;

    public OrderController(StripePaymentService stripePaymentService, OrderRepository orderRepository) {
        this.stripePaymentService = stripePaymentService;
        this.orderRepository = orderRepository;
    }

    /**
     * Cria pedido e gera Sessão de Checkout autenticada na Stripe (exige usuário logado via JWT).
     */
    @PostMapping("/checkout")
    public ResponseEntity<Map<String, Object>> checkout(
            @Valid @RequestBody CheckoutRequest request,
            Authentication authentication) {
        String userId = (String) authentication.getPrincipal();
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(stripePaymentService.createCheckoutSession(userId, request));
    }

    /**
     * Lista o histórico de pedidos do usuário autenticado.
     */
    @GetMapping("/my")
    public ResponseEntity<List<Order>> listMyOrders(Authentication authentication) {
        String userId = (String) authentication.getPrincipal();
        return ResponseEntity.ok(orderRepository.findByUserIdOrderByCreatedAtDesc(userId));
    }
}
