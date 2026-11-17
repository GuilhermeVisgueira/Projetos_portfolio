package com.visgueira.ecommerce.model;

/**
 * Perfis de conta no E-commerce:
 * - CUSTOMER: Consumidor (compra produtos, gerencia carrinho e pedidos)
 * - SELLER: Vendedor (cadastra/gerencia seus próprios produtos e acompanha vendas)
 * - ADMIN: Administrador da plataforma
 */
public enum Role {
    CUSTOMER,
    SELLER,
    ADMIN
}
