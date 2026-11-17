package com.visgueira.ecommerce.controller;

import com.visgueira.ecommerce.service.StripePaymentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/webhooks/stripe")
public class StripeWebhookController {

    private final StripePaymentService stripePaymentService;

    public StripeWebhookController(StripePaymentService stripePaymentService) {
        this.stripePaymentService = stripePaymentService;
    }

    /**
     * Endpoint oficial que recebe o Webhook da Stripe e valida o cabeçalho criptográfico Stripe-Signature.
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> handleStripeWebhook(
            @RequestBody String payload,
            @RequestHeader(value = "Stripe-Signature", required = false) String sigHeader) {
        return ResponseEntity.ok(stripePaymentService.processAuthenticatedWebhook(payload, sigHeader));
    }

    /**
     * Endpoint de simulação para testes locais:
     * Constrói um payload real do evento checkout.session.completed da Stripe,
     * assina com HMAC-SHA256 (Stripe-Signature) e passa pelo validador oficial!
     */
    @PostMapping("/simulate/{stripeSessionId}")
    public ResponseEntity<Map<String, Object>> simulateAuthenticatedWebhook(@PathVariable String stripeSessionId) {
        String simulatedPayload = """
            {
              "id": "evt_test_webhook_%d",
              "object": "event",
              "api_version": "2024-06-20",
              "type": "checkout.session.completed",
              "data": {
                "object": {
                  "id": "%s",
                  "object": "checkout.session",
                  "payment_status": "paid",
                  "status": "complete"
                }
              }
            }
            """.formatted(System.currentTimeMillis(), stripeSessionId).trim();

        String validStripeSignature = stripePaymentService.generateTestWebhookSignature(simulatedPayload);
        return ResponseEntity.ok(stripePaymentService.processAuthenticatedWebhook(simulatedPayload, validStripeSignature));
    }
}
