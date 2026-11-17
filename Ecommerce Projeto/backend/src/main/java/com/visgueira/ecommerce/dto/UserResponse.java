package com.visgueira.ecommerce.dto;

import com.visgueira.ecommerce.model.Address;
import com.visgueira.ecommerce.model.User;

import java.time.Instant;
import java.util.Date;

/**
 * DTO seguro de resposta para Usuários (Consumidores, Vendedores e Admins).
 * Nunca expõe passwordHash nem verificationToken.
 */
public record UserResponse(
    String id,
    String name,
    String email,
    String cpf,
    Date birthDate,
    String phone,
    Address address,
    String role,
    String storeName,
    String cnpj,
    Boolean emailVerified,
    Instant createdAt
) {
    public static UserResponse fromEntity(User user) {
        return new UserResponse(
            user.getId(),
            user.getName(),
            user.getEmail(),
            user.getCpf(),
            user.getBirthDate(),
            user.getPhone(),
            user.getAddress(),
            user.getRole(),
            user.getStoreName(),
            user.getCnpj(),
            user.getEmailVerified() != null ? user.getEmailVerified() : false,
            user.getCreatedAt()
        );
    }
}
