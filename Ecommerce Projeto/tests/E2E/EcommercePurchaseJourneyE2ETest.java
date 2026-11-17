package E2E;

import Fixtures.EcommerceTestFixtures;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.visgueira.ecommerce.EcommerceApiApplication;
import com.visgueira.ecommerce.model.Coupon;
import com.visgueira.ecommerce.repository.*;
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
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = EcommerceApiApplication.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.data.mongodb.uri=mongodb://localhost:27017/ecommerce_test_e2e?replicaSet=rs0&directConnection=true",
    "app.stripe.api-key=sk_test_mock_local_dev",
    "app.stripe.webhook-secret=whsec_test_e2e_secret"
})
public class EcommercePurchaseJourneyE2ETest {

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
    private StripePaymentService stripePaymentService;

    @MockBean
    private EmailVerificationService emailVerificationService;

    @BeforeEach
    void resetDatabase() {
        orderRepository.deleteAll();
        cartRepository.deleteAll();
        couponRepository.deleteAll();
        productRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void CompletePurchaseJourney_FromRegistrationToCartShippingAndStripeWebhook_ShouldCompleteOrderDecrementStockAndClearCart() throws Exception {
        // =====================================================================
        // Arrange: Cria cupom ativo de 10% (BEMVINDO10) no banco
        // =====================================================================
        Coupon coupon = EcommerceTestFixtures.createActiveCoupon("BEMVINDO10", 10, 10000L, 10000L);
        couponRepository.save(coupon);

        // =====================================================================
        // Act & Assert — Passo 1: Vendedor cria conta, verifica e-mail, faz login e publica produto
        // =====================================================================
        MvcResult sellerRegRes = mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "name", "Carla Vendedora E2E",
                    "email", "carla.seller@e2e.com",
                    "cpf", "111.333.555-77",
                    "password", "SellerPass123",
                    "role", "SELLER",
                    "storeName", "Carla Pro Audio"
                ))))
            .andExpect(status().isCreated())
            .andReturn();

        String sellerOtp = objectMapper.readTree(sellerRegRes.getResponse().getContentAsString())
            .get("devVerificationCode").asText();

        mockMvc.perform(post("/api/v1/auth/verify-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", "carla.seller@e2e.com", "token", sellerOtp))))
            .andExpect(status().isOk());

        MvcResult sellerLoginRes = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", "carla.seller@e2e.com", "password", "SellerPass123"))))
            .andExpect(status().isOk())
            .andReturn();

        String sellerToken = objectMapper.readTree(sellerLoginRes.getResponse().getContentAsString())
            .get("accessToken").asText();

        // Vendedora cadastra produto custando R$ 200,00 (20000 centavos), peso 1200g, estoque 5
        mockMvc.perform(post("/api/v1/products")
                .header("Authorization", "Bearer " + sellerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "sku", "E2E-MIC-001",
                    "name", "Microfone Condensador USB Studio",
                    "priceCents", 20000,
                    "weightGrams", 1200,
                    "stock", 5,
                    "category", "Áudio",
                    "attributes", Map.of("pattern", "Cardioide", "sampleRate", "96kHz")
                ))))
            .andExpect(status().isCreated());

        // =====================================================================
        // Passo 2: Consumidor cria conta, verifica e-mail e obtém Token JWT
        // =====================================================================
        MvcResult custRegRes = mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "name", "Pedro Consumidor E2E",
                    "email", "pedro.customer@e2e.com",
                    "cpf", "222.444.666-88",
                    "password", "CustomerPass123",
                    "role", "CUSTOMER",
                    "address", EcommerceTestFixtures.createSudesteAddress("01310-100")
                ))))
            .andExpect(status().isCreated())
            .andReturn();

        String custOtp = objectMapper.readTree(custRegRes.getResponse().getContentAsString())
            .get("devVerificationCode").asText();

        mockMvc.perform(get("/api/v1/auth/verify-email")
                .param("email", "pedro.customer@e2e.com")
                .param("token", custOtp))
            .andExpect(status().isOk());

        MvcResult custLoginRes = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", "pedro.customer@e2e.com", "password", "CustomerPass123"))))
            .andExpect(status().isOk())
            .andReturn();

        String custToken = objectMapper.readTree(custLoginRes.getResponse().getContentAsString())
            .get("accessToken").asText();

        // =====================================================================
        // Passo 3: Consumidor adiciona 2 unidades ao Carrinho, aplica Cupom e calcula Frete PAC
        // =====================================================================
        // 2 unidades x 20000 = Subtotal 40000 centavos (R$ 400,00), Peso Total = 2400g
        mockMvc.perform(post("/api/v1/cart/items")
                .header("Authorization", "Bearer " + custToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("sku", "E2E-MIC-001", "quantity", 2))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.subtotalCents").value(40000));

        // Aplica cupom BEMVINDO10 (10% de 40000 = 4000 centavos de desconto -> Líquido 36000)
        mockMvc.perform(post("/api/v1/cart/coupon")
                .header("Authorization", "Bearer " + custToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("code", "BEMVINDO10"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.discountCents").value(4000))
            .andExpect(jsonPath("$.totalCents").value(36000));

        // Seleciona frete PAC para CEP 01310-100 (Sudeste base 1890 + 2kg extras iniciados [2400g] = 1890 + 1000 = 2890 centavos)
        mockMvc.perform(post("/api/v1/cart/shipping")
                .header("Authorization", "Bearer " + custToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("zipCode", "01310-100", "serviceType", "PAC"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.shippingCents").value(2890))
            .andExpect(jsonPath("$.totalCents").value(38890)); // 36000 + 2890

        // =====================================================================
        // Passo 4: Consumidor finaliza Checkout a partir do Carrinho
        // =====================================================================
        MvcResult checkoutRes = mockMvc.perform(post("/api/v1/orders/checkout")
                .header("Authorization", "Bearer " + custToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.subtotalCents").value(40000))
            .andExpect(jsonPath("$.discountCents").value(4000))
            .andExpect(jsonPath("$.shippingCents").value(2890))
            .andExpect(jsonPath("$.totalAmountCents").value(38890))
            .andReturn();

        JsonNode checkoutJson = objectMapper.readTree(checkoutRes.getResponse().getContentAsString());
        String stripeSessionId = checkoutJson.get("stripeSessionId").asText();

        // Verifica que o carrinho foi esvaziado após gerar o pedido
        mockMvc.perform(get("/api/v1/cart")
                .header("Authorization", "Bearer " + custToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items").isEmpty())
            .andExpect(jsonPath("$.totalCents").value(0));

        // =====================================================================
        // Passo 5: Stripe envia Webhook autenticado -> Pedido vira PAID e estoque cai de 5 para 3
        // =====================================================================
        String webhookPayload = EcommerceTestFixtures.buildStripeCheckoutCompletedPayload(stripeSessionId);
        String validSignature = stripePaymentService.generateTestWebhookSignature(webhookPayload);

        mockMvc.perform(post("/api/v1/webhooks/stripe")
                .header("Stripe-Signature", validSignature)
                .contentType(MediaType.APPLICATION_JSON)
                .content(webhookPayload))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PAID"));

        // Verifica estoque atualizado no catálogo (5 - 2 = 3)
        assertEquals(3, productRepository.findBySku("E2E-MIC-001").orElseThrow().getStock());
    }
}
