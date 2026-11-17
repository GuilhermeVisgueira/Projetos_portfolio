package Fixtures;

import com.visgueira.ecommerce.model.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

/**
 * Builders e geradores reutilizáveis de dados e payloads para a Pirâmide de Testes.
 */
public final class EcommerceTestFixtures {

    private EcommerceTestFixtures() {
    }

    public static Product createSampleProduct(String sku, long priceCents, int stock, int weightGrams) {
        Product product = new Product();
        product.setId("prod-" + sku.toLowerCase());
        product.setSku(sku);
        product.setName("Produto Teste " + sku);
        product.setDescription("Descrição técnica de teste para " + sku);
        product.setPriceCents(priceCents);
        product.setWeightGrams(weightGrams);
        product.setCurrency("BRL");
        product.setStock(stock);
        product.setCategory("Eletrônicos");
        product.setSellerId("seller-001");
        product.setSellerStoreName("Loja Tech Teste");
        product.setAttributes(Map.of("warrantyMonths", 12, "voltage", "Bivolt"));
        product.setActive(true);
        product.setCreatedAt(Instant.now());
        return product;
    }

    public static Coupon createActiveCoupon(String code, int discountPct, Long maxDiscountCents, Long minOrderCents) {
        return new Coupon(
            code,
            discountPct,
            maxDiscountCents,
            minOrderCents,
            Instant.now().plus(7, ChronoUnit.DAYS),
            true
        );
    }

    public static Coupon createExpiredCoupon(String code) {
        return new Coupon(
            code,
            15,
            5000L,
            10000L,
            Instant.now().minus(1, ChronoUnit.DAYS),
            true
        );
    }

    public static Address createSudesteAddress(String zipCode) {
        return new Address(
            "Avenida Paulista",
            "1000",
            "Conjunto 101",
            "Bela Vista",
            "São Paulo",
            "SP",
            zipCode,
            "Brasil"
        );
    }

    public static String buildStripeCheckoutCompletedPayload(String stripeSessionId) {
        return """
            {
              "id": "evt_test_%d",
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
    }
}
