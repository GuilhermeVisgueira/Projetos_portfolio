package com.visgueira.ecommerce.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import com.visgueira.ecommerce.dto.CheckoutRequest;
import com.visgueira.ecommerce.dto.ShippingQuoteResponse;
import com.visgueira.ecommerce.model.*;
import com.visgueira.ecommerce.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

@Service
public class StripePaymentService {

    private static final Logger log = LoggerFactory.getLogger(StripePaymentService.class);

    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final CartService cartService;
    private final CouponRepository couponRepository;
    private final ShippingService shippingService;
    private final EmailVerificationService emailVerificationService;
    private final ObjectMapper objectMapper;

    private final String stripeApiKey;
    private final String webhookSecret;
    private final String successUrl;
    private final String cancelUrl;

    public StripePaymentService(
            ProductRepository productRepository,
            OrderRepository orderRepository,
            UserRepository userRepository,
            CartService cartService,
            CouponRepository couponRepository,
            ShippingService shippingService,
            EmailVerificationService emailVerificationService,
            ObjectMapper objectMapper,
            @Value("${app.stripe.api-key}") String stripeApiKey,
            @Value("${app.stripe.webhook-secret}") String webhookSecret,
            @Value("${app.stripe.success-url}") String successUrl,
            @Value("${app.stripe.cancel-url}") String cancelUrl) {
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.cartService = cartService;
        this.couponRepository = couponRepository;
        this.shippingService = shippingService;
        this.emailVerificationService = emailVerificationService;
        this.objectMapper = objectMapper;
        this.stripeApiKey = stripeApiKey;
        this.webhookSecret = webhookSecret;
        this.successUrl = successUrl;
        this.cancelUrl = cancelUrl;
        Stripe.apiKey = stripeApiKey;
    }

    /**
     * Cria sessão de pagamento a partir do Carrinho do usuário ou da lista explícita de itens,
     * calculando subtotal, cupom de desconto, frete (PAC/SEDEX) e total final.
     */
    public Map<String, Object> createCheckoutSession(String userId, CheckoutRequest request) {
        User customer = userRepository.findById(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuário não encontrado."));

        Cart userCart = cartService.getOrCreateCart(userId);
        List<CheckoutRequest.Item> itemsToProcess = new ArrayList<>();

        boolean usingPersistedCart = (request == null || request.items() == null || request.items().isEmpty());
        if (usingPersistedCart) {
            if (userCart.getItems().isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O carrinho está vazio. Adicione itens antes de finalizar a compra.");
            }
            for (CartItem ci : userCart.getItems()) {
                itemsToProcess.add(new CheckoutRequest.Item(ci.getSku(), ci.getQuantity()));
            }
        } else {
            itemsToProcess.addAll(request.items());
        }

        List<OrderItem> orderItems = new ArrayList<>();
        long subtotalCents = 0L;
        int totalWeightGrams = 0;

        for (CheckoutRequest.Item reqItem : itemsToProcess) {
            if (reqItem.quantity() <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A quantidade do item deve ser maior que zero.");
            }
            Product product = productRepository.findBySku(reqItem.sku())
                .orElseThrow(() -> new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Produto não encontrado para o SKU: " + reqItem.sku()
                ));

            if (!Boolean.TRUE.equals(product.getActive())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Produto inativo: " + product.getName());
            }
            if (product.getStock() == null || product.getStock() < reqItem.quantity()) {
                throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Estoque insuficiente para '" + product.getName() + "'. Disponível: " + product.getStock()
                );
            }

            orderItems.add(new OrderItem(
                product.getId(),
                product.getSku(),
                product.getName(),
                product.getSellerId(),
                product.getPriceCents(),
                reqItem.quantity()
            ));
            subtotalCents += product.getPriceCents() * reqItem.quantity();
            totalWeightGrams += product.getWeightGrams() * reqItem.quantity();
        }

        // Cupom de desconto
        String couponCode = (request != null && request.couponCode() != null && !request.couponCode().isBlank())
            ? request.couponCode().trim().toUpperCase()
            : (usingPersistedCart ? userCart.getCouponCode() : null);

        long discountCents = 0L;
        if (couponCode != null && !couponCode.isBlank()) {
            Coupon coupon = couponRepository.findByCodeIgnoreCase(couponCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cupom não encontrado: " + couponCode));
            cartService.validateCouponForSubtotal(coupon, subtotalCents);
            discountCents = cartService.computeCouponDiscountCents(coupon, subtotalCents);
        }

        long netSubtotalCents = Math.max(0L, subtotalCents - discountCents);

        // Endereço e cálculo de frete
        Address shippingAddress = (request != null && request.shippingAddress() != null)
            ? request.shippingAddress()
            : customer.getAddress();

        String zipCode = (shippingAddress != null && shippingAddress.getZipCode() != null && !shippingAddress.getZipCode().isBlank())
            ? shippingAddress.getZipCode()
            : userCart.getZipCode();

        String shippingServiceType = (request != null && request.shippingService() != null && !request.shippingService().isBlank())
            ? request.shippingService().trim().toUpperCase()
            : (userCart.getShippingService() != null ? userCart.getShippingService() : "PAC");

        long shippingCents = 0L;
        if (zipCode != null && !zipCode.isBlank()) {
            ShippingQuoteResponse quote = shippingService.calculateShipping(zipCode, totalWeightGrams, netSubtotalCents, shippingServiceType);
            shippingCents = quote.shippingCents();
            shippingServiceType = quote.serviceType();
        }

        long totalAmountCents = netSubtotalCents + shippingCents;

        Order order = new Order();
        order.setUserId(customer.getId());
        order.setCustomerEmail(customer.getEmail());
        order.setStatus("PENDING");
        order.setItems(orderItems);
        order.setSubtotalCents(subtotalCents);
        order.setCouponCode(couponCode);
        order.setDiscountCents(discountCents);
        order.setShippingService(shippingServiceType);
        order.setShippingCents(shippingCents);
        order.setTotalAmountCents(totalAmountCents);
        order.setCurrency("BRL");
        order.setShippingAddress(shippingAddress);
        order.setCreatedAt(Instant.now());

        order = orderRepository.save(order);

        boolean isRealStripeKey = stripeApiKey != null
            && (stripeApiKey.startsWith("sk_test_") || stripeApiKey.startsWith("sk_live_"))
            && !stripeApiKey.equals("sk_test_mock_local_dev");

        if (isRealStripeKey) {
            try {
                SessionCreateParams.Builder paramsBuilder = SessionCreateParams.builder()
                    .setMode(SessionCreateParams.Mode.PAYMENT)
                    .setCustomerEmail(customer.getEmail())
                    .setClientReferenceId(order.getId())
                    .setSuccessUrl(successUrl)
                    .setCancelUrl(cancelUrl)
                    .putMetadata("orderId", order.getId());

                for (OrderItem item : orderItems) {
                    paramsBuilder.addLineItem(
                        SessionCreateParams.LineItem.builder()
                            .setQuantity(item.getQuantity().longValue())
                            .setPriceData(
                                SessionCreateParams.LineItem.PriceData.builder()
                                    .setCurrency("brl")
                                    .setUnitAmount(item.getUnitPriceCents())
                                    .setProductData(
                                        SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                            .setName(item.getName())
                                            .putMetadata("sku", item.getSku())
                                            .build()
                                    )
                                    .build()
                            )
                            .build()
                    );
                }

                Session session = Session.create(paramsBuilder.build());
                order.setStripeSessionId(session.getId());
                order.setStripeCheckoutUrl(session.getUrl());
                order = orderRepository.save(order);
            } catch (Exception e) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Erro ao criar sessão na Stripe: " + e.getMessage());
            }
        } else {
            String mockSessionId = "cs_test_" + UUID.randomUUID().toString().replace("-", "");
            String mockUrl = "https://checkout.stripe.com/pay/" + mockSessionId;
            order.setStripeSessionId(mockSessionId);
            order.setStripeCheckoutUrl(mockUrl);
            order = orderRepository.save(order);
        }

        if (usingPersistedCart) {
            cartService.clearCart(userId);
        }

        return Map.of(
            "message", "Sessão de pagamento criada com sucesso.",
            "stripeMode", isRealStripeKey ? "LIVE_STRIPE_API" : "LOCAL_SIMULATION",
            "orderId", order.getId(),
            "stripeSessionId", order.getStripeSessionId(),
            "checkoutUrl", order.getStripeCheckoutUrl(),
            "subtotalCents", order.getSubtotalCents(),
            "discountCents", order.getDiscountCents(),
            "shippingCents", order.getShippingCents(),
            "totalAmountCents", order.getTotalAmountCents(),
            "order", order
        );
    }

    /**
     * Autentica a assinatura criptográfica (Stripe-Signature HMAC-SHA256) enviada pela Stripe
     * e executa a transação ACID (@Transactional) para baixar o estoque e marcar o pedido como PAID.
     */
    @Transactional
    public Map<String, Object> processAuthenticatedWebhook(String payload, String sigHeader) {
        if (sigHeader == null || sigHeader.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Cabeçalho Stripe-Signature ausente.");
        }

        String eventType;
        String stripeSessionId;

        try {
            Event event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
            eventType = event.getType();

            JsonNode root = objectMapper.readTree(payload);
            stripeSessionId = root.path("data").path("object").path("id").asText(null);
        } catch (SignatureVerificationException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Assinatura Stripe-Signature inválida: " + e.getMessage());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payload de webhook inválido: " + e.getMessage());
        }

        if ("checkout.session.expired".equals(eventType) || "payment_intent.payment_failed".equals(eventType)) {
            return markOrderAsFailedInTransaction(stripeSessionId);
        }

        if (!"checkout.session.completed".equals(eventType)) {
            return Map.of("received", true, "ignoredEventType", eventType);
        }

        if (stripeSessionId == null || stripeSessionId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ID da sessão Stripe não encontrado no evento.");
        }

        return confirmOrderPaymentInTransaction(stripeSessionId);
    }

    @Transactional
    public Map<String, Object> confirmOrderPaymentInTransaction(String stripeSessionId) {
        Order order = orderRepository.findByStripeSessionId(stripeSessionId)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Pedido não encontrado para a sessão Stripe: " + stripeSessionId
            ));

        if ("PAID".equals(order.getStatus())) {
            return Map.of(
                "message", "Pedido já havia sido processado anteriormente (idempotente).",
                "idempotent", true,
                "orderId", order.getId(),
                "status", order.getStatus()
            );
        }

        for (OrderItem item : order.getItems()) {
            Product product = productRepository.findBySku(item.getSku())
                .orElseThrow(() -> new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Produto não encontrado durante confirmação: " + item.getSku()
                ));

            if (product.getStock() < item.getQuantity()) {
                throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Estoque insuficiente no momento da confirmação para o SKU: " + item.getSku()
                );
            }

            product.setStock(product.getStock() - item.getQuantity());
            productRepository.save(product);
        }

        order.setStatus("PAID");
        order.setPaidAt(Instant.now());
        orderRepository.save(order);

        emailVerificationService.sendOrderPaidConfirmationEmail(order);

        log.info("Transação ACID concluída: Pedido #{} atualizado para PAID e estoque decrementado.", order.getId());

        return Map.of(
            "message", "Pagamento autenticado via Stripe! Pedido atualizado para PAID e estoque baixado em transação ACID.",
            "idempotent", false,
            "orderId", order.getId(),
            "stripeSessionId", stripeSessionId,
            "status", order.getStatus(),
            "paidAt", order.getPaidAt()
        );
    }

    @Transactional
    public Map<String, Object> markOrderAsFailedInTransaction(String stripeSessionId) {
        if (stripeSessionId == null) {
            return Map.of("received", true, "status", "IGNORED");
        }
        Order order = orderRepository.findByStripeSessionId(stripeSessionId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido não encontrado: " + stripeSessionId));

        if (!"PAID".equals(order.getStatus())) {
            order.setStatus("FAILED");
            orderRepository.save(order);
        }
        return Map.of(
            "message", "Pagamento falhou ou expirou. Estoque preservado.",
            "orderId", order.getId(),
            "status", order.getStatus()
        );
    }

    public String generateTestWebhookSignature(String payload) {
        try {
            long timestamp = Instant.now().getEpochSecond();
            String signedPayload = timestamp + "." + payload;
            Mac sha256Hmac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            sha256Hmac.init(secretKey);
            byte[] hashBytes = sha256Hmac.doFinal(signedPayload.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hashBytes) {
                hex.append(String.format("%02x", b));
            }
            return "t=" + timestamp + ",v1=" + hex;
        } catch (Exception e) {
            throw new RuntimeException("Erro ao assinar payload de teste", e);
        }
    }
}
