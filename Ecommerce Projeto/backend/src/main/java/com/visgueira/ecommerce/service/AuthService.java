package com.visgueira.ecommerce.service;

import com.visgueira.ecommerce.dto.AuthResponse;
import com.visgueira.ecommerce.dto.LoginRequest;
import com.visgueira.ecommerce.dto.RegisterRequest;
import com.visgueira.ecommerce.dto.UserResponse;
import com.visgueira.ecommerce.model.Role;
import com.visgueira.ecommerce.model.User;
import com.visgueira.ecommerce.repository.UserRepository;
import com.visgueira.ecommerce.security.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

@Service
public class AuthService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final EmailVerificationService emailVerificationService;
    private final long verificationExpirationMinutes;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            EmailVerificationService emailVerificationService,
            @Value("${app.security.email-verification.expiration-minutes:30}") long verificationExpirationMinutes) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.emailVerificationService = emailVerificationService;
        this.verificationExpirationMinutes = verificationExpirationMinutes;
    }

    /**
     * Cria conta para Consumidor (CUSTOMER) ou Vendedor (SELLER) e envia código de verificação por e-mail.
     */
    public Map<String, Object> register(RegisterRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase();
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe uma conta cadastrada com este e-mail.");
        }
        if (userRepository.existsByCpf(request.cpf().trim())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe uma conta cadastrada com este CPF.");
        }

        // Define e valida o papel (CUSTOMER para consumidor, SELLER para vendedor)
        Role accountRole = Role.CUSTOMER;
        if (request.role() != null && !request.role().isBlank()) {
            try {
                accountRole = Role.valueOf(request.role().trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tipo de conta inválido. Use CUSTOMER (Consumidor) ou SELLER (Vendedor).");
            }
        }
        if (accountRole == Role.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Não é permitido auto-cadastro como ADMIN.");
        }
        if (accountRole == Role.SELLER && (request.storeName() == null || request.storeName().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Para contas de Vendedor (SELLER), o campo 'storeName' (nome da loja) é obrigatório.");
        }

        String verificationCode = generateSixDigitCode();

        User user = new User();
        user.setName(request.name().trim());
        user.setEmail(normalizedEmail);
        user.setCpf(request.cpf().trim());
        user.setBirthDate(request.birthDate());
        user.setPhone(request.phone());
        user.setAddress(request.address());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(accountRole.name());
        user.setStoreName(accountRole == Role.SELLER ? request.storeName().trim() : null);
        user.setCnpj(accountRole == Role.SELLER ? request.cnpj() : null);

        user.setEmailVerified(false);
        user.setVerificationToken(verificationCode);
        user.setVerificationTokenExpiresAt(Instant.now().plus(verificationExpirationMinutes, ChronoUnit.MINUTES));
        user.setCreatedAt(Instant.now());

        User saved = userRepository.save(user);
        emailVerificationService.sendAccountVerificationEmail(saved, verificationCode);

        return Map.of(
            "message", "Conta criada com sucesso! Verifique seu e-mail para ativar a conta.",
            "accountType", saved.getRole(),
            "email", saved.getEmail(),
            "emailVerified", false,
            "devVerificationCode", verificationCode, // Facilita testes imediatos de API/Blazor em ambiente de desenvolvimento
            "user", UserResponse.fromEntity(saved)
        );
    }

    /**
     * Valida o código/token de verificação de e-mail e ativa a conta.
     */
    public Map<String, Object> verifyEmail(String email, String token) {
        User user = userRepository.findByEmail(email.trim().toLowerCase())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuário não encontrado."));

        if (Boolean.TRUE.equals(user.getEmailVerified())) {
            return Map.of("message", "E-mail já estava verificado.", "emailVerified", true);
        }

        if (user.getVerificationToken() == null || !user.getVerificationToken().equals(token.trim())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Código/token de verificação inválido.");
        }

        if (user.getVerificationTokenExpiresAt() != null && Instant.now().isAfter(user.getVerificationTokenExpiresAt())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Código de verificação expirado. Solicite um novo envio.");
        }

        user.setEmailVerified(true);
        user.setVerificationToken(null);
        user.setVerificationTokenExpiresAt(null);
        userRepository.save(user);

        return Map.of(
            "message", "E-mail verificado com sucesso! Sua conta está ativa.",
            "email", user.getEmail(),
            "role", user.getRole(),
            "emailVerified", true
        );
    }

    /**
     * Reenvia um novo código de verificação para o e-mail do usuário.
     */
    public Map<String, Object> resendVerificationCode(String email) {
        User user = userRepository.findByEmail(email.trim().toLowerCase())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuário não encontrado."));

        if (Boolean.TRUE.equals(user.getEmailVerified())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Este e-mail já foi verificado.");
        }

        String newCode = generateSixDigitCode();
        user.setVerificationToken(newCode);
        user.setVerificationTokenExpiresAt(Instant.now().plus(verificationExpirationMinutes, ChronoUnit.MINUTES));
        userRepository.save(user);

        emailVerificationService.sendAccountVerificationEmail(user, newCode);

        return Map.of(
            "message", "Novo código de verificação enviado para o e-mail.",
            "email", user.getEmail(),
            "devVerificationCode", newCode
        );
    }

    /**
     * Autentica Consumidor, Vendedor ou Admin e emite Token JWT assinado.
     */
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email().trim().toLowerCase())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "E-mail ou senha inválidos."));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "E-mail ou senha inválidos.");
        }

        if (!Boolean.TRUE.equals(user.getEmailVerified())) {
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "E-mail ainda não verificado. Confirme o código enviado para seu e-mail antes de fazer login."
            );
        }

        String token = jwtService.generateToken(user);
        return new AuthResponse(token, "Bearer", jwtService.getExpirationMs(), UserResponse.fromEntity(user));
    }

    public UserResponse getAuthenticatedUser(String userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuário não encontrado."));
        return UserResponse.fromEntity(user);
    }

    private String generateSixDigitCode() {
        int number = 100000 + SECURE_RANDOM.nextInt(900000);
        return String.valueOf(number);
    }
}
