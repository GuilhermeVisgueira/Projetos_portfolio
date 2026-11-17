package com.visgueira.ecommerce.service;

import com.visgueira.ecommerce.dto.ShippingQuoteResponse;
import com.visgueira.ecommerce.model.Cart;
import com.visgueira.ecommerce.model.CartItem;
import com.visgueira.ecommerce.model.Coupon;
import com.visgueira.ecommerce.model.Product;
import com.visgueira.ecommerce.repository.CartRepository;
import com.visgueira.ecommerce.repository.CouponRepository;
import com.visgueira.ecommerce.repository.ProductRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;

@Service
public class CartService {

    public static final int MAX_QUANTITY_PER_ITEM = 20;

    private final CartRepository cartRepository;
    private final ProductRepository productRepository;
    private final CouponRepository couponRepository;
    private final ShippingService shippingService;

    public CartService(
            CartRepository cartRepository,
            ProductRepository productRepository,
            CouponRepository couponRepository,
            ShippingService shippingService) {
        this.cartRepository = cartRepository;
        this.productRepository = productRepository;
        this.couponRepository = couponRepository;
        this.shippingService = shippingService;
    }

    public Cart getOrCreateCart(String userId) {
        return cartRepository.findByUserId(userId)
            .orElseGet(() -> cartRepository.save(new Cart(userId)));
    }

    /**
     * Adiciona ou atualiza a quantidade de um item no carrinho, validando limites, estoque e recalculando totais/frete.
     */
    public Cart addOrUpdateItem(String userId, String sku, int quantity) {
        if (sku == null || sku.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O SKU do produto é obrigatório.");
        }
        if (quantity <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A quantidade deve ser maior que zero.");
        }
        if (quantity > MAX_QUANTITY_PER_ITEM) {
            throw new ResponseStatusException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "Limite máximo de " + MAX_QUANTITY_PER_ITEM + " unidades por item no carrinho excedido."
            );
        }

        Product product = productRepository.findBySku(sku.trim())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Produto não encontrado: " + sku));

        if (!Boolean.TRUE.equals(product.getActive())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Produto inativo e indisponível para compra.");
        }
        if (product.getStock() == null || product.getStock() < quantity) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Estoque insuficiente para '" + product.getName() + "'. Disponível: " + product.getStock()
            );
        }

        Cart cart = getOrCreateCart(userId);
        Optional<CartItem> existing = cart.getItems().stream()
            .filter(i -> i.getSku().equalsIgnoreCase(product.getSku()))
            .findFirst();

        if (existing.isPresent()) {
            existing.get().setQuantity(quantity);
            existing.get().setUnitPriceCents(product.getPriceCents());
            existing.get().setWeightGrams(product.getWeightGrams());
        } else {
            cart.getItems().add(new CartItem(
                product.getId(),
                product.getSku(),
                product.getName(),
                product.getSellerId(),
                product.getPriceCents(),
                product.getWeightGrams(),
                quantity
            ));
        }

        recalculateCartTotals(cart);
        return cartRepository.save(cart);
    }

    public Cart removeItem(String userId, String sku) {
        Cart cart = getOrCreateCart(userId);
        cart.getItems().removeIf(i -> i.getSku().equalsIgnoreCase(sku));
        recalculateCartTotals(cart);
        return cartRepository.save(cart);
    }

    /**
     * Aplica cupom de desconto validando existência, status ativo, expiração e pedido mínimo.
     */
    public Cart applyCoupon(String userId, String couponCode) {
        if (couponCode == null || couponCode.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe um código de cupom válido.");
        }

        Cart cart = getOrCreateCart(userId);
        if (cart.getItems().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Não é possível aplicar cupom em um carrinho vazio.");
        }

        Coupon coupon = couponRepository.findByCodeIgnoreCase(couponCode.trim())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cupom não encontrado: " + couponCode));

        validateCouponForSubtotal(coupon, calculateRawSubtotal(cart));

        cart.setCouponCode(coupon.getCode().toUpperCase());
        recalculateCartTotals(cart);
        return cartRepository.save(cart);
    }

    /**
     * Calcula e aplica o frete (PAC ou SEDEX) ao carrinho com base no CEP e peso dos itens.
     */
    public Cart applyShipping(String userId, String zipCode, String serviceType) {
        Cart cart = getOrCreateCart(userId);
        if (cart.getItems().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Adicione itens ao carrinho antes de calcular o frete.");
        }

        cart.setZipCode(shippingService.validateAndNormalizeZipCode(zipCode));
        cart.setShippingService(serviceType != null && !serviceType.isBlank() ? serviceType.trim().toUpperCase() : "PAC");
        recalculateCartTotals(cart);
        return cartRepository.save(cart);
    }

    public void clearCart(String userId) {
        Cart cart = getOrCreateCart(userId);
        cart.getItems().clear();
        cart.setCouponCode(null);
        cart.setDiscountCents(0L);
        cart.setShippingCents(0L);
        cart.setEstimatedDeliveryDays(null);
        cart.setSubtotalCents(0L);
        cart.setTotalCents(0L);
        cart.setUpdatedAt(Instant.now());
        cartRepository.save(cart);
    }

    public void recalculateCartTotals(Cart cart) {
        if (cart.getItems() == null || cart.getItems().isEmpty()) {
            cart.setSubtotalCents(0L);
            cart.setCouponCode(null);
            cart.setDiscountCents(0L);
            cart.setShippingCents(0L);
            cart.setEstimatedDeliveryDays(null);
            cart.setTotalCents(0L);
            cart.setUpdatedAt(Instant.now());
            return;
        }

        long subtotal = calculateRawSubtotal(cart);
        cart.setSubtotalCents(subtotal);

        // Recalcula desconto de cupom se houver
        long discount = 0L;
        if (cart.getCouponCode() != null && !cart.getCouponCode().isBlank()) {
            Optional<Coupon> optCoupon = couponRepository.findByCodeIgnoreCase(cart.getCouponCode());
            if (optCoupon.isPresent() && isCouponValidForSubtotal(optCoupon.get(), subtotal)) {
                discount = computeCouponDiscountCents(optCoupon.get(), subtotal);
            } else {
                cart.setCouponCode(null);
            }
        }
        cart.setDiscountCents(discount);

        long netSubtotal = Math.max(0L, subtotal - discount);

        // Recalcula frete se houver CEP configurado
        long shippingCents = 0L;
        if (cart.getZipCode() != null && !cart.getZipCode().isBlank()) {
            int totalWeightGrams = cart.getItems().stream()
                .mapToInt(i -> i.getWeightGrams() * i.getQuantity())
                .sum();
            String service = cart.getShippingService() != null ? cart.getShippingService() : "PAC";
            ShippingQuoteResponse quote = shippingService.calculateShipping(cart.getZipCode(), totalWeightGrams, netSubtotal, service);
            shippingCents = quote.shippingCents();
            cart.setShippingService(quote.serviceType());
            cart.setEstimatedDeliveryDays(quote.estimatedDeliveryDays());
        }
        cart.setShippingCents(shippingCents);
        cart.setTotalCents(netSubtotal + shippingCents);
        cart.setUpdatedAt(Instant.now());
    }

    public void validateCouponForSubtotal(Coupon coupon, long subtotalCents) {
        if (!Boolean.TRUE.equals(coupon.getActive())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Este cupom está inativo.");
        }
        if (coupon.getExpiresAt() != null && Instant.now().isAfter(coupon.getExpiresAt())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Este cupom está expirado.");
        }
        if (coupon.getMinOrderCents() != null && subtotalCents < coupon.getMinOrderCents()) {
            throw new ResponseStatusException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "Valor mínimo do pedido para este cupom é R$ %.2f".formatted(coupon.getMinOrderCents() / 100.0)
            );
        }
    }

    public long computeCouponDiscountCents(Coupon coupon, long subtotalCents) {
        long rawDiscount = Math.round(subtotalCents * (coupon.getDiscountPercentage() / 100.0));
        if (coupon.getMaxDiscountCents() != null && coupon.getMaxDiscountCents() > 0) {
            rawDiscount = Math.min(rawDiscount, coupon.getMaxDiscountCents());
        }
        return Math.min(rawDiscount, subtotalCents);
    }

    private boolean isCouponValidForSubtotal(Coupon coupon, long subtotalCents) {
        if (!Boolean.TRUE.equals(coupon.getActive())) return false;
        if (coupon.getExpiresAt() != null && Instant.now().isAfter(coupon.getExpiresAt())) return false;
        return coupon.getMinOrderCents() == null || subtotalCents >= coupon.getMinOrderCents();
    }

    private long calculateRawSubtotal(Cart cart) {
        return cart.getItems().stream()
            .mapToLong(i -> i.getUnitPriceCents() * i.getQuantity())
            .sum();
    }
}
