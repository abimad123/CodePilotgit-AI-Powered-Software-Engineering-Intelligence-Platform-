package dev.codepilotgit.auth;

import dev.codepilotgit.common.ApiException;
import dev.codepilotgit.user.User;
import dev.codepilotgit.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    @Mock UserRepository users;
    @Mock RefreshTokenRepository refreshTokens;
    @Mock PasswordEncoder passwords;
    @Mock JwtEncoder encoder;
    AuthService service;
    @BeforeEach void setUp() {
        service = new AuthService(users, refreshTokens, passwords, encoder, "codepilotgit", "api", 15, 14);
    }
    @Test void registrationNormalizesEmailAndHashesPassword() {
        when(passwords.encode("long-password-123")).thenReturn("hash");
        when(users.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        var result = service.register("Alice", " ALICE@Example.com ", "long-password-123");
        assertEquals("alice@example.com", result.email());
        verify(passwords).encode("long-password-123");
        verify(users).save(argThat(u -> u.passwordHash.equals("hash") && !u.passwordHash.equals("long-password-123")));
    }
    @Test void duplicateEmailRejected() {
        when(users.existsByEmail("alice@example.com")).thenReturn(true);
        assertThrows(ApiException.class, () -> service.register("Alice", "ALICE@example.com", "long-password-123"));
        verify(users, never()).save(any());
    }
    @Test void invalidPasswordRejected() {
        User user = new User("Alice", "alice@example.com", "hash");
        when(users.findByEmail(user.email)).thenReturn(Optional.of(user));
        assertThrows(ApiException.class, () -> service.login(user.email, "wrong"));
        verifyNoInteractions(refreshTokens);
    }
    @Test void loginAndRefreshRotateToken() {
        User user = new User("Alice", "alice@example.com", "hash");
        when(users.findByEmail(user.email)).thenReturn(Optional.of(user));
        when(passwords.matches("long-password-123", "hash")).thenReturn(true);
        when(encoder.encode(any(JwtEncoderParameters.class))).thenReturn(new Jwt("signed", Instant.now(), Instant.now().plusSeconds(900), Map.of("alg", "HS256"), Map.of("sub", user.id.toString())));
        when(refreshTokens.saveAndFlush(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));
        var first = service.login(user.email, "long-password-123");
        assertEquals("signed", first.accessToken());
        assertEquals(43, first.refreshToken().length());
        var token = new RefreshToken(user, hash(first.refreshToken()), Instant.now().plusSeconds(3600));
        when(refreshTokens.findByTokenHash(anyString())).thenAnswer(inv -> {
            String value = inv.getArgument(0);
            if (value.equals(token.tokenHash)) return Optional.of(token);
            return Optional.of(new RefreshToken(user, value, Instant.now().plusSeconds(3600)));
        });
        var second = service.refresh(first.refreshToken());
        assertNotEquals(first.refreshToken(), second.refreshToken());
        assertNotNull(token.revokedAt);
        assertThrows(ApiException.class, () -> service.refresh(first.refreshToken()));
    }
    @Test void expiredAndRevokedRefreshRejected() {
        User user = new User("Alice", "alice@example.com", "hash");
        RefreshToken token = new RefreshToken(user, "x", Instant.now().minusSeconds(1));
        when(refreshTokens.findByTokenHash(anyString())).thenReturn(Optional.of(token));
        String raw = "a".repeat(43);
        assertThrows(ApiException.class, () -> service.refresh(raw));
        token.expiresAt = Instant.now().plusSeconds(60); token.revokedAt = Instant.now();
        assertThrows(ApiException.class, () -> service.refresh(raw));
    }
    private String hash(String raw) {
        try { return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(raw.getBytes(java.nio.charset.StandardCharsets.US_ASCII))); }
        catch (Exception ex) { throw new AssertionError(ex); }
    }
}
