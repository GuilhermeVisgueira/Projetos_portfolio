package com.visgueira.ecommerce.dto;

import com.visgueira.ecommerce.model.Address;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Date;

/**
 * DTO de criação de conta para Consumidores (CUSTOMER) e Vendedores (SELLER).
 */
public record RegisterRequest(
    @NotBlank(message = "O nome é obrigatório")
    String name,

    @NotBlank(message = "O e-mail é obrigatório")
    @Email(message = "Formato de e-mail inválido")
    String email,

    @NotBlank(message = "O CPF é obrigatório")
    String cpf,

    Date birthDate,
    String phone,
    Address address,

    @NotBlank(message = "A senha é obrigatória")
    @Size(min = 6, message = "A senha deve ter pelo menos 6 caracteres")
    String password,

    /**
     * Tipo de conta: "CUSTOMER" (Consumidor - padrão) ou "SELLER" (Vendedor).
     */
    String role,

    /**
     * Nome da loja (obrigatório quando role == "SELLER").
     */
    String storeName,

    /**
     * CNPJ opcional para vendedores.
     */
    String cnpj
) {}
