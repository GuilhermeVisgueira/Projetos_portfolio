package com.visgueira.ecommerce.dto;

import com.visgueira.ecommerce.model.Address;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * Permite realizar checkout informando itens avulsos OU usando o Carrinho ativo do usuário (quando items for nulo/vazio).
 */
public record CheckoutRequest(
    @Valid
    List<Item> items,

    String couponCode,
    String shippingService,
    Address shippingAddress
) {
    public record Item(
        @NotBlank(message = "O SKU do produto é obrigatório")
        String sku,

        @Min(value = 1, message = "A quantidade mínima é 1")
        int quantity
    ) {}
}
