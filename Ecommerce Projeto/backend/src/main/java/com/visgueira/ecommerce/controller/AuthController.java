package com.visgueira.ecommerce.controller;

import com.visgueira.ecommerce.dto.*;
import com.visgueira.ecommerce.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Cria conta de Consumidor (CUSTOMER) ou Vendedor (SELLER) e envia código de verificação por e-mail.
     */
    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    /**
     * Confirma o e-mail via POST (enviando email + código de 6 dígitos no JSON).
     */
    @PostMapping("/verify-email")
    public ResponseEntity<Map<String, Object>> verifyEmailPost(@Valid @RequestBody VerifyEmailRequest request) {
        return ResponseEntity.ok(authService.verifyEmail(request.email(), request.token()));
    }

    /**
     * Confirma o e-mail via GET (clicando direto no link enviado por e-mail).
     */
    @GetMapping("/verify-email")
    public ResponseEntity<Map<String, Object>> verifyEmailGet(
            @RequestParam String email,
            @RequestParam String token) {
        return ResponseEntity.ok(authService.verifyEmail(email, token));
    }

    /**
     * Reenvia código de verificação para o e-mail informado.
     */
    @PostMapping("/resend-verification")
    public ResponseEntity<Map<String, Object>> resendVerification(@RequestBody Map<String, String> body) {
        String email = body.getOrDefault("email", "");
        return ResponseEntity.ok(authService.resendVerificationCode(email));
    }

    /**
     * Realiza login (exige e-mail verificado) e devolve o Token JWT assinado.
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    /**
     * Retorna os dados da conta autenticada pelo cabeçalho Authorization: Bearer <token>.
     */
    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(Authentication authentication) {
        String userId = (String) authentication.getPrincipal();
        return ResponseEntity.ok(authService.getAuthenticatedUser(userId));
    }
}
