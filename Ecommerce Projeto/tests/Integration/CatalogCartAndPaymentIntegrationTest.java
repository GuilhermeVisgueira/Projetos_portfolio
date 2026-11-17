package Integration;

import Fixtures.EcommerceTestFixtures;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.visgueira.ecommerce.EcommerceApiApplication;
import com.visgueira.ecommerce.model.*;
import com.visgueira.ecommerce.repository.*;
import com.visgueira.ecommerce.security.JwtService;
import com.visgueira.ecommerce.service.EmailVerificationService;
import com.visgueira.ecommerce.service.StripePaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = EcommerceApiApplication.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.data.mongodb.uri=mongodb://localhost:27017/ecommerce_test_integration?replicaSet=rs0&directConnection=true",
    "app.stripe.api-key=sk_test_mock_local_dev",
    "app.stripe.webhook-secret=whsec_test_integration_secret"
})
public class CatalogCartAndPaymentIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private StripePaymentService stripePaymentService;

    // Mockamos apenas o disparo SMTP externo para validar as interações de notificação
    @MockBean
    private EmailVerificationService emailVerificationService;

    private User verifiedSeller;
    private User verifiedCustomer;
    private String sellerJwt;
    private String customerJwt;

    @BeforeEach
    void cleanDatabaseAndSetupUsers() {
        orderRepository.deleteAll();
        cartRepository.deleteAll();
        couponRepository.deleteAll();
        productRepository.deleteAll();
        userRepository.deleteAll();

        verifiedSeller = new User();
        verifiedSeller.setName("Vendedor Integração");
        verifiedSeller.setEmail("seller.int@loja.com");
        verifiedSeller.setCpf("100.200.300-40");
        verifiedSeller.setRole("SELLER");
        verifiedSeller.setStoreName("Tech Store Integração");
        verifiedSeller.setEmailVerified(true);
        verifiedSeller = userRepository.save(verifiedSeller);
        sellerJwt = jwtService.generateToken(verifiedSeller);

        verifiedCustomer = new User();
        verifiedCustomer.setName("Consumidor Integração");
        verifiedCustomer.setEmail("customer.int@cliente.com");
        verifiedCustomer.setCpf("500.600.700-80");
        verifiedCustomer.setRole("CUSTOMER");
        verifiedCustomer.setAddress(EcommerceTestFixtures.createSudesteAddress("01310-100"));
        verifiedCustomer.setEmailVerified(true);
        verifiedCustomer = userRepository.save(verifiedCustomer);
        customerJwt = jwtService.generateToken(verifiedCustomer);
    }

    @Test
    void CreateProduct_WhenAuthenticatedAsSeller_ShouldPersistPolymorphicProductWith201() throws Exception {
        // Arrange
        Map<String, Object> payload = Map.of(
            "sku", "INT-BOOK-001",
            "name", "Livro Engenharia de Testes",
            "description", "Guia completo de Pirâmide de Testes",
            "priceCents", 9500,
            "weightGrams", 650,
            "stock", 25,
            "category", "Livros",
            "attributes", Map.of("isbn", "978-85-11111-22-3", "pages", 380)
        );

        // Act & Assert
        mockMvc.perform(post("/api/v1/products")
                .header("Authorization", "Bearer " + sellerJwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.sku").value("INT-BOOK-001"))
            .andExpect(jsonPath("$.sellerId").value(verifiedSeller.getId()))
            .andExpect(jsonPath("$.sellerStoreName").value("Tech Store Integração"))
            .andExpect(jsonPath("$.attributes.isbn").value("978-85-11111-22-3"));
    }

    @Test
    void CreateProduct_WhenAuthenticatedAsCustomer_ShouldBlockAccessWith403Forbidden() throws Exception {
        // Arrange
        Map<String, Object> payload = Map.of(
            "sku", "FORBIDDEN-SKU",
            "name", "Produto Proibido para Cliente Criar",
            "priceCents", 5000,
            "stock", 10,
            "category", "Outros"
        );

        // Act & Assert
        mockMvc.perform(post("/api/v1/products")
                .header("Authorization", "Bearer " + customerJwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload)))
            .andExpect(status().isForbidden());
    }

    @Test
    void GetCart_WhenUnauthenticated_ShouldBlockAccessWith403Forbidden() throws Exception {
        // Arrange & Act & Assert
        mockMvc.perform(get("/api/v1/cart"))
            .andExpect(status().isForbidden());
    }

    @Test
    void ProcessAuthenticatedWebhook_WhenSignatureIsInvalid_ShouldRejectWith401Unauthorized() throws Exception {
        // Arrange
        String rawPayload = EcommerceTestFixtures.buildStripeCheckoutCompletedPayload("cs_test_qualquer");

        // Act & Assert
        mockMvc.perform(post("/api/v1/webhooks/stripe")
                .header("Stripe-Signature", "t=1700000000,v1=assinatura_falsa_invalida")
                .contentType(MediaType.APPLICATION_JSON)
                .content(rawPayload))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void ProcessAuthenticatedWebhook_WhenSignatureValidAndDuplicateEventReceived_ShouldDecrementStockOnceAndBeIdempotent() throws Exception {
        // Arrange
        Product prod = productRepository.save(EcommerceTestFixtures.createSampleProduct("IDEMP-001", 10000L, 10, 500));
        Order pendingOrder = new Order();
        pendingOrder.setUserId(verifiedCustomer.getId());
        pendingOrder.setCustomerEmail(verifiedCustomer.getEmail());
        pendingOrder.setStatus("PENDING");
        pendingOrder.setItems(List.of(new OrderItem(prod.getId(), prod.getSku(), prod.getName(), prod.getSellerId(), 10000L, 3)));
        pendingOrder.setTotalAmountCents(30000L);
        pendingOrder.setStripeSessionId("cs_test_idempotency_999");
        orderRepository.save(pendingOrder);

        String payload = EcommerceTestFixtures.buildStripeCheckoutCompletedPayload("cs_test_idempotency_999");
        String validSignature = stripePaymentService.generateTestWebhookSignature(payload);

        // Act (1ª chamada: processa o pagamento e baixa estoque de 10 -> 7)
        mockMvc.perform(post("/api/v1/webhooks/stripe")
                .header("Stripe-Signature", validSignature)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PAID"))
            .andExpect(jsonPath("$.idempotent").value(false));

        // Act (2ª chamada duplicada da Stripe com o mesmo evento: deve ser idempotente e NÃO baixar o estoque novamente!)
        mockMvc.perform(post("/api/v1/webhooks/stripe")
                .header("Stripe-Signature", validSignature)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PAID"))
            .andExpect(jsonPath("$.idempotent").value(true));

        // Assert
        Product updatedProduct = productRepository.findBySku("IDEMP-001").orElseThrow();
        assertEquals(7, updatedProduct.getStock()); // Decrementou apenas 1 vez (10 - 3 = 7)
        verify(emailVerificationService, times(1)).sendOrderPaidConfirmationEmail(any(Order.class));
    }

    @Test
    void ProcessAuthenticatedWebhook_WhenStockBecomesInsufficientDuringWebhook_ShouldRollbackTransactionAndKeepOrderPending() throws Exception {
        // Arrange: Pedido de 2 produtos onde o segundo ficou sem estoque antes do webhook chegar
        Product prodOk = productRepository.save(EcommerceTestFixtures.createSampleProduct("TX-OK-01", 10000L, 5, 500));
        Product prodOut = productRepository.save(EcommerceTestFixtures.createSampleProduct("TX-OUT-02", 10000L, 0, 500));

        Order order = new Order();
        order.setUserId(verifiedCustomer.getId());
        order.setCustomerEmail(verifiedCustomer.getEmail());
        order.setStatus("PENDING");
        order.setItems(List.of(
            new OrderItem(prodOk.getId(), prodOk.getSku(), prodOk.getName(), prodOk.getSellerId(), 10000L, 2),
            new OrderItem(prodOut.getId(), prodOut.getSku(), prodOut.getName(), prodOut.getSellerId(), 10000L, 1)
        ));
        order.setTotalAmountCents(30000L);
        order.setStripeSessionId("cs_test_rollback_acid");
        orderRepository.save(order);

        String payload = EcommerceTestFixtures.buildStripeCheckoutCompletedPayload("cs_test_rollback_acid");
        String validSignature = stripePaymentService.generateTestWebhookSignature(payload);

        // Act: O segundo item falha com 409 CONFLICT, forçando Rollback ACID no primeiro item (TX-OK-01 deve continuar com estoque 5!)
        mockMvc.perform(post("/api/v1/webhooks/stripe")
                .header("Stripe-Signature", validSignature)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isConflict());

        // Assert: Verifica atomicidade ACID do Replica Set rs0
        Product afterRollback = productRepository.findBySku("TX-OK-01").orElseThrow();
        Order orderAfterRollback = orderRepository.findByStripeSessionId("cs_test_rollback_acid").orElseThrow();
        assertEquals(5, afterRollback.getStock()); // Revertido pelo Rollback ACID!
        assertEquals("PENDING", orderAfterRollback.getStatus());
    }
}
