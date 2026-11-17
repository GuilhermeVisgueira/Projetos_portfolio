package Unit;

import Fixtures.EcommerceTestFixtures;
import com.visgueira.ecommerce.dto.AuthResponse;
import com.visgueira.ecommerce.dto.LoginRequest;
import com.visgueira.ecommerce.dto.RegisterRequest;
import com.visgueira.ecommerce.model.User;
import com.visgueira.ecommerce.repository.UserRepository;
import com.visgueira.ecommerce.security.JwtService;
import com.visgueira.ecommerce.service.AuthService;
import com.visgueira.ecommerce.service.EmailVerificationService;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AuthAndSecurityUnitTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private EmailVerificationService emailVerificationService;

    private PasswordEncoder passwordEncoder;
    private JwtService jwtService;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        jwtService = new JwtService("ChaveSecretaUnitTestsEcommercePortfolio2026ComMaisDe256Bits!", 3600000L);
        authService = new AuthService(userRepository, passwordEncoder, jwtService, emailVerificationService, 30L);
    }

    @Test
    void Register_WhenCustomerDataIsValid_ShouldHashPasswordGenerateOtpAndTriggerEmail() {
        // Arrange
        RegisterRequest req = new RegisterRequest(
            "Lucas Cliente",
            "lucas@cliente.com",
            "123.123.123-99",
            null,
            "+55 11 99999-0000",
            EcommerceTestFixtures.createSudesteAddress("01310-100"),
            "SenhaSegura123",
            "CUSTOMER",
            null,
            null
        );
        when(userRepository.existsByEmail("lucas@cliente.com")).thenReturn(false);
        when(userRepository.existsByCpf("123.123.123-99")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId("user-lucas-id");
            return u;
        });

        // Act
        Map<String, Object> response = authService.register(req);

        // Assert
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();

        assertNotEquals("SenhaSegura123", savedUser.getPasswordHash());
        assertTrue(passwordEncoder.matches("SenhaSegura123", savedUser.getPasswordHash()));
        assertFalse(savedUser.getEmailVerified());
        assertNotNull(savedUser.getVerificationToken());
        assertEquals(6, savedUser.getVerificationToken().length());
        assertEquals("CUSTOMER", response.get("accountType"));
        verify(emailVerificationService, times(1)).sendAccountVerificationEmail(eq(savedUser), eq(savedUser.getVerificationToken()));
    }

    @Test
    void Register_WhenSellerHasNoStoreName_ShouldThrowBadRequestException() {
        // Arrange
        RegisterRequest invalidSellerReq = new RegisterRequest(
            "Marcos Vendedor",
            "marcos@loja.com",
            "321.321.321-00",
            null,
            null,
            null,
            "Senha123",
            "SELLER",
            "   ", // Nome da loja em branco
            null
        );
        when(userRepository.existsByEmail("marcos@loja.com")).thenReturn(false);
        when(userRepository.existsByCpf("321.321.321-00")).thenReturn(false);

        // Act & Assert
        ResponseStatusException ex = assertThrows(
            ResponseStatusException.class,
            () -> authService.register(invalidSellerReq)
        );
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verify(userRepository, never()).save(any());
    }

    @Test
    void Register_WhenUserAttemptsSelfRegistrationAsAdmin_ShouldThrowForbiddenException() {
        // Arrange
        RegisterRequest adminAttemptReq = new RegisterRequest(
            "Hacker",
            "hacker@ataque.com",
            "999.888.777-66",
            null,
            null,
            null,
            "Senha123",
            "ADMIN",
            null,
            null
        );
        when(userRepository.existsByEmail("hacker@ataque.com")).thenReturn(false);
        when(userRepository.existsByCpf("999.888.777-66")).thenReturn(false);

        // Act & Assert
        ResponseStatusException ex = assertThrows(
            ResponseStatusException.class,
            () -> authService.register(adminAttemptReq)
        );
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        verify(userRepository, never()).save(any());
    }

    @Test
    void VerifyEmail_WhenTokenMatchesAndNotExpired_ShouldActivateUserAndClearToken() {
        // Arrange
        User unverifiedUser = new User();
        unverifiedUser.setId("u-1");
        unverifiedUser.setEmail("cliente@teste.com");
        unverifiedUser.setRole("CUSTOMER");
        unverifiedUser.setEmailVerified(false);
        unverifiedUser.setVerificationToken("654321");
        unverifiedUser.setVerificationTokenExpiresAt(Instant.now().plus(15, ChronoUnit.MINUTES));

        when(userRepository.findByEmail("cliente@teste.com")).thenReturn(Optional.of(unverifiedUser));

        // Act
        Map<String, Object> result = authService.verifyEmail("cliente@teste.com", "654321");

        // Assert
        assertTrue((Boolean) result.get("emailVerified"));
        assertTrue(unverifiedUser.getEmailVerified());
        assertNull(unverifiedUser.getVerificationToken());
        assertNull(unverifiedUser.getVerificationTokenExpiresAt());
        verify(userRepository, times(1)).save(unverifiedUser);
    }

    @Test
    void VerifyEmail_WhenTokenIsExpired_ShouldThrowBadRequestException() {
        // Arrange
        User expiredTokenUser = new User();
        expiredTokenUser.setEmail("expirado@teste.com");
        expiredTokenUser.setEmailVerified(false);
        expiredTokenUser.setVerificationToken("111222");
        expiredTokenUser.setVerificationTokenExpiresAt(Instant.now().minus(1, ChronoUnit.MINUTES));

        when(userRepository.findByEmail("expirado@teste.com")).thenReturn(Optional.of(expiredTokenUser));

        // Act & Assert
        ResponseStatusException ex = assertThrows(
            ResponseStatusException.class,
            () -> authService.verifyEmail("expirado@teste.com", "111222")
        );
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertFalse(expiredTokenUser.getEmailVerified());
    }

    @Test
    void Login_WhenEmailIsNotVerified_ShouldBlockLoginWithForbiddenException() {
        // Arrange
        User unverified = new User();
        unverified.setId("u-2");
        unverified.setEmail("pendente@teste.com");
        unverified.setPasswordHash(passwordEncoder.encode("MinhaSenha123"));
        unverified.setEmailVerified(false);

        when(userRepository.findByEmail("pendente@teste.com")).thenReturn(Optional.of(unverified));

        // Act & Assert
        ResponseStatusException ex = assertThrows(
            ResponseStatusException.class,
            () -> authService.login(new LoginRequest("pendente@teste.com", "MinhaSenha123"))
        );
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void Login_WhenCredentialsValidAndEmailVerified_ShouldReturnSignedJwtTokenWithRoleClaim() {
        // Arrange
        User verifiedSeller = new User();
        verifiedSeller.setId("seller-777");
        verifiedSeller.setName("Loja Oficial");
        verifiedSeller.setEmail("vendedor@oficial.com");
        verifiedSeller.setPasswordHash(passwordEncoder.encode("SenhaVendedor123"));
        verifiedSeller.setRole("SELLER");
        verifiedSeller.setStoreName("Oficial Tech");
        verifiedSeller.setEmailVerified(true);

        when(userRepository.findByEmail("vendedor@oficial.com")).thenReturn(Optional.of(verifiedSeller));

        // Act
        AuthResponse authResponse = authService.login(new LoginRequest("vendedor@oficial.com", "SenhaVendedor123"));

        // Assert
        assertNotNull(authResponse.accessToken());
        assertEquals("Bearer", authResponse.tokenType());
        Claims claims = jwtService.validateAndExtractClaims(authResponse.accessToken());
        assertEquals("seller-777", claims.getSubject());
        assertEquals("SELLER", claims.get("role", String.class));
        assertEquals("vendedor@oficial.com", claims.get("email", String.class));
    }
}
