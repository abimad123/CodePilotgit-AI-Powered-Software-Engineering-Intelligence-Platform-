package dev.codepilotgit.auth;

import dev.codepilotgit.common.ApiException;
import dev.codepilotgit.user.User;
import dev.codepilotgit.user.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class AuthService {
    public record UserView(UUID id, String name, String email) {}
    public record TokenView(String accessToken, String refreshToken, String tokenType, long expiresIn) {}
    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwords;
    private final JwtEncoder encoder;
    private final SecureRandom random = new SecureRandom();
    private final String issuer, audience;
    private final long accessMinutes, refreshDays;
    public AuthService(UserRepository users, RefreshTokenRepository refreshTokens, PasswordEncoder passwords, JwtEncoder encoder,
                       @Value("${app.jwt.issuer}") String issuer, @Value("${app.jwt.audience}") String audience,
                       @Value("${app.jwt.access-minutes}") long accessMinutes, @Value("${app.jwt.refresh-days}") long refreshDays) {
        this.users = users; this.refreshTokens = refreshTokens; this.passwords = passwords; this.encoder = encoder;
        this.issuer = issuer; this.audience = audience; this.accessMinutes = accessMinutes; this.refreshDays = refreshDays;
        if (accessMinutes < 1 || accessMinutes > 60 || refreshDays < 1 || refreshDays > 90) throw new IllegalArgumentException("Invalid token lifetime");
    }
    @Transactional
    public UserView register(String name, String email, String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72)
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Password is too long for the configured password encoder.");
        String normalized = email.trim().toLowerCase(java.util.Locale.ROOT);
        if (users.existsByEmail(normalized)) throw new ApiException(HttpStatus.CONFLICT, "EMAIL_ALREADY_REGISTERED", "Email is already registered.");
        User user = users.save(new User(name.trim(), normalized, passwords.encode(password)));
        return view(user);
    }
    @Transactional
    public TokenView login(String email, String password) {
        User user = users.findByEmail(email.trim().toLowerCase(java.util.Locale.ROOT))
                .orElseThrow(AuthService::invalidCredentials);
        if (!"ACTIVE".equals(user.status) || !passwords.matches(password, user.passwordHash)) throw invalidCredentials();
        return issue(user);
    }
    @Transactional
    public TokenView refresh(String raw) {
        RefreshToken previous = refreshTokens.findByTokenHash(hash(raw)).orElseThrow(AuthService::invalidRefresh);
        if (previous.revokedAt != null || !previous.expiresAt.isAfter(Instant.now()) || !"ACTIVE".equals(previous.user.getStatus())) throw invalidRefresh();
        TokenView next = issue(previous.user);
        previous.revokedAt = Instant.now();
        previous.replacedByTokenId = refreshTokens.findByTokenHash(hash(next.refreshToken())).orElseThrow().id;
        return next;
    }
    @Transactional
    public void logout(String raw) {
        RefreshToken token = refreshTokens.findByTokenHash(hash(raw)).orElse(null);
        if (token != null && token.revokedAt == null) token.revokedAt = Instant.now();
    }
    @Transactional(readOnly = true)
    public UserView me(UUID userId) {
        User user = users.findById(userId).filter(u -> "ACTIVE".equals(u.status))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentication required."));
        return view(user);
    }
    private TokenView issue(User user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer(issuer).audience(List.of(audience))
                .subject(user.getId().toString()).issuedAt(now).expiresAt(now.plus(Duration.ofMinutes(accessMinutes)))
                .id(UUID.randomUUID().toString()).build();
        String access = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        refreshTokens.saveAndFlush(new RefreshToken(user, hash(raw), now.plus(Duration.ofDays(refreshDays))));
        return new TokenView(access, raw, "Bearer", Duration.ofMinutes(accessMinutes).toSeconds());
    }
    private String hash(String raw) {
        if (raw == null || raw.length() != 43 || !raw.matches("[A-Za-z0-9_-]+")) throw invalidRefresh();
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.US_ASCII))); }
        catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
    private UserView view(User user) { return new UserView(user.getId(), user.getName(), user.getEmail()); }
    private static ApiException invalidCredentials() { return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid email or password."); }
    private static ApiException invalidRefresh() { return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "Invalid refresh token."); }
}
