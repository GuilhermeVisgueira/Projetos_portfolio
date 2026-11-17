package Unit;

import Fixtures.EcommerceTestFixtures;
import com.visgueira.ecommerce.dto.ShippingQuoteResponse;
import com.visgueira.ecommerce.model.Cart;
import com.visgueira.ecommerce.model.Coupon;
import com.visgueira.ecommerce.model.Product;
import com.visgueira.ecommerce.repository.CartRepository;
import com.visgueira.ecommerce.repository.CouponRepository;
import com.visgueira.ecommerce.repository.ProductRepository;
import com.visgueira.ecommerce.service.CartService;
import com.visgueira.ecommerce.service.ShippingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class CartAndShippingUnitTest {

    @Mock
    private CartRepository cartRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CouponRepository couponRepository;

    private ShippingService shippingService;
    private CartService cartService;

    @BeforeEach
    void setUp() {
        shippingService = new ShippingService();
        cartService = new CartService(cartRepository, productRepository, couponRepository, shippingService);
    }

    @Test
    void CalculateShipping_WhenSudesteValidZipCodeAndUnderThreshold_ShouldReturnPacFreightAndDays() {
        // Arrange
        String cepSudeste = "01310-100";
        int weightGrams = 800; // <= 1kg (sem sobretaxa de peso)
        long netSubtotalCents = 25000L; // R$ 250,00 (abaixo de R$ 500,00)

        // Act
        ShippingQuoteResponse quote = shippingService.calculateShipping(cepSudeste, weightGrams, netSubtotalCents, "PAC");

        // Assert
        assertEquals("01310100", quote.zipCode());
        assertEquals("Sudeste", quote.region());
        assertEquals("PAC", quote.serviceType());
        assertEquals(1890L, quote.shippingCents());
        assertEquals(5, quote.estimatedDeliveryDays());
        assertFalse(quote.freeShippingApplied());
    }

    @Test
    void CalculateShipping_WhenNetSubtotalReachesFreeShippingThreshold_ShouldReturnZeroFreightForPac() {
        // Arrange
        String cepNordeste = "40010-000";
        int weightGrams = 2500;
        long netSubtotalCents = 50000L; // Exatamente R$ 500,00 (limite de frete grátis PAC)

        // Act
        ShippingQuoteResponse pacQuote = shippingService.calculateShipping(cepNordeste, weightGrams, netSubtotalCents, "PAC");
        ShippingQuoteResponse sedexQuote = shippingService.calculateShipping(cepNordeste, weightGrams, netSubtotalCents, "SEDEX");

        // Assert
        assertEquals(0L, pacQuote.shippingCents());
        assertTrue(pacQuote.freeShippingApplied());
        assertTrue(sedexQuote.shippingCents() > 0L);
        assertFalse(sedexQuote.freeShippingApplied());
    }

    @Test
    void CalculateShipping_WhenWeightExceedsOneKilogram_ShouldAddWeightSurchargePerExtraKg() {
        // Arrange
        String cepSul = "80010-000"; // Base Sul PAC = 2490 centavos
        int weightGrams = 2500;      // 2.5kg -> 2kg extras acima do primeiro kg (+1000 centavos)
        long netSubtotalCents = 10000L;

        // Act
        ShippingQuoteResponse quote = shippingService.calculateShipping(cepSul, weightGrams, netSubtotalCents, "PAC");

        // Assert
        assertEquals(3490L, quote.shippingCents()); // 2490 + (2 * 500)
        assertEquals("Sul", quote.region());
    }

    @Test
    void CalculateShipping_WhenZipCodeIsInvalidOrNegativeWeight_ShouldThrowBadRequestException() {
        // Arrange
        String invalidCep = "123";

        // Act & Assert
        ResponseStatusException cepEx = assertThrows(
            ResponseStatusException.class,
            () -> shippingService.calculateShipping(invalidCep, 500, 10000L, "PAC")
        );
        assertEquals(HttpStatus.BAD_REQUEST, cepEx.getStatusCode());

        ResponseStatusException weightEx = assertThrows(
            ResponseStatusException.class,
            () -> shippingService.calculateShipping("01310-100", 0, 10000L, "PAC")
        );
        assertEquals(HttpStatus.BAD_REQUEST, weightEx.getStatusCode());
    }

    @Test
    void AddOrUpdateItem_WhenValidProductAndStockAvailable_ShouldCalculateSubtotalAndTotalCorrectly() {
        // Arrange
        String userId = "user-123";
        Product notebook = EcommerceTestFixtures.createSampleProduct("NB-001", 300000L, 10, 1800);
        Cart existingCart = new Cart(userId);

        when(productRepository.findBySku("NB-001")).thenReturn(Optional.of(notebook));
        when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(existingCart));
        when(cartRepository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        Cart updatedCart = cartService.addOrUpdateItem(userId, "NB-001", 2);

        // Assert
        assertEquals(1, updatedCart.getItems().size());
        assertEquals(2, updatedCart.getItems().get(0).getQuantity());
        assertEquals(600000L, updatedCart.getSubtotalCents());
        assertEquals(600000L, updatedCart.getTotalCents());
        verify(cartRepository, times(1)).save(existingCart);
    }

    @Test
    void AddOrUpdateItem_WhenQuantityIsZeroOrNegative_ShouldThrowBadRequestException() {
        // Arrange
        String userId = "user-123";

        // Act & Assert
        ResponseStatusException zeroEx = assertThrows(
            ResponseStatusException.class,
            () -> cartService.addOrUpdateItem(userId, "NB-001", 0)
        );
        assertEquals(HttpStatus.BAD_REQUEST, zeroEx.getStatusCode());

        ResponseStatusException negativeEx = assertThrows(
            ResponseStatusException.class,
            () -> cartService.addOrUpdateItem(userId, "NB-001", -3)
        );
        assertEquals(HttpStatus.BAD_REQUEST, negativeEx.getStatusCode());
        verifyNoInteractions(productRepository, cartRepository);
    }

    @Test
    void AddOrUpdateItem_WhenQuantityExceedsMaxLimitPerItem_ShouldThrowUnprocessableEntityException() {
        // Arrange
        String userId = "user-123";
        int excessiveQuantity = CartService.MAX_QUANTITY_PER_ITEM + 1;

        // Act & Assert
        ResponseStatusException ex = assertThrows(
            ResponseStatusException.class,
            () -> cartService.addOrUpdateItem(userId, "NB-001", excessiveQuantity)
        );
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.getStatusCode());
        verifyNoInteractions(productRepository, cartRepository);
    }

    @Test
    void AddOrUpdateItem_WhenRequestedQuantityExceedsAvailableStock_ShouldThrowConflictException() {
        // Arrange
        String userId = "user-123";
        Product scarceProduct = EcommerceTestFixtures.createSampleProduct("GPU-001", 450000L, 2, 1200);
        when(productRepository.findBySku("GPU-001")).thenReturn(Optional.of(scarceProduct));

        // Act & Assert
        ResponseStatusException ex = assertThrows(
            ResponseStatusException.class,
            () -> cartService.addOrUpdateItem(userId, "GPU-001", 5)
        );
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(cartRepository, never()).save(any());
    }

    @Test
    void ApplyCoupon_WhenCouponIsValidAndActive_ShouldApplyPercentageDiscountRespectingCap() {
        // Arrange
        String userId = "user-123";
        Product item = EcommerceTestFixtures.createSampleProduct("MON-001", 200000L, 5, 3000); // R$ 2.000,00
        Cart cart = new Cart(userId);
        cart.getItems().add(new com.visgueira.ecommerce.model.CartItem(
            item.getId(), item.getSku(), item.getName(), item.getSellerId(), item.getPriceCents(), item.getWeightGrams(), 1
        ));

        // Cupom de 20% com teto máximo de R$ 150,00 (15000 centavos)
        Coupon cappedCoupon = EcommerceTestFixtures.createActiveCoupon("TECH20", 20, 15000L, 50000L);

        when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(cart));
        when(couponRepository.findByCodeIgnoreCase("TECH20")).thenReturn(Optional.of(cappedCoupon));
        when(cartRepository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        Cart result = cartService.applyCoupon(userId, "TECH20");

        // Assert
        assertEquals("TECH20", result.getCouponCode());
        assertEquals(200000L, result.getSubtotalCents());
        assertEquals(15000L, result.getDiscountCents()); // 20% seria 40000, mas respeitou o teto de 15000
        assertEquals(185000L, result.getTotalCents());
    }

    @Test
    void ApplyCoupon_WhenCouponIsExpired_ShouldThrowUnprocessableEntityException() {
        // Arrange
        String userId = "user-123";
        Cart cart = new Cart(userId);
        cart.getItems().add(new com.visgueira.ecommerce.model.CartItem(
            "p1", "SKU-1", "Item", "s1", 20000L, 500, 1
        ));
        Coupon expiredCoupon = EcommerceTestFixtures.createExpiredCoupon("EXPIRADO15");

        when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(cart));
        when(couponRepository.findByCodeIgnoreCase("EXPIRADO15")).thenReturn(Optional.of(expiredCoupon));

        // Act & Assert
        ResponseStatusException ex = assertThrows(
            ResponseStatusException.class,
            () -> cartService.applyCoupon(userId, "EXPIRADO15")
        );
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.getStatusCode());
    }

    @Test
    void ApplyCoupon_WhenSubtotalIsBelowCouponMinimumOrder_ShouldThrowUnprocessableEntityException() {
        // Arrange
        String userId = "user-123";
        Cart cart = new Cart(userId);
        cart.getItems().add(new com.visgueira.ecommerce.model.CartItem(
            "p1", "SKU-1", "Item Barato", "s1", 5000L, 200, 1 // R$ 50,00
        ));
        // Exige pedido mínimo de R$ 100,00 (10000 centavos)
        Coupon minOrderCoupon = EcommerceTestFixtures.createActiveCoupon("MIN100", 10, null, 10000L);

        when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(cart));
        when(couponRepository.findByCodeIgnoreCase("MIN100")).thenReturn(Optional.of(minOrderCoupon));

        // Act & Assert
        ResponseStatusException ex = assertThrows(
            ResponseStatusException.class,
            () -> cartService.applyCoupon(userId, "MIN100")
        );
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.getStatusCode());
    }
}
