package dev.codepilotgit.auth;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class JwtConfigTest {
    @Test void validatesSignatureIssuerAudienceAndExpiry() {
        JwtConfig config = new JwtConfig();
        byte[] bytes = new byte[32]; new java.security.SecureRandom().nextBytes(bytes);
        var key = config.jwtKey(Base64.getEncoder().encodeToString(bytes));
        JwtEncoder encoder = config.jwtEncoder(key);
        JwtDecoder decoder = config.jwtDecoder(key, "issuer", "api");
        Instant now = Instant.now();
        String valid = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),
                JwtClaimsSet.builder().issuer("issuer").audience(List.of("api")).subject("user")
                        .issuedAt(now).expiresAt(now.plusSeconds(60)).build())).getTokenValue();
        assertEquals("user", decoder.decode(valid).getSubject());
        String[] parts = valid.split("\\.");
        String invalid = parts[0] + "." + parts[1] + "." + (parts[2].charAt(0) == 'A' ? 'B' : 'A') + parts[2].substring(1);
        assertThrows(JwtException.class, () -> decoder.decode(invalid));
        String wrongAudience = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),
                JwtClaimsSet.builder().issuer("issuer").audience(List.of("other")).subject("user")
                        .issuedAt(now).expiresAt(now.plusSeconds(60)).build())).getTokenValue();
        assertThrows(JwtException.class, () -> decoder.decode(wrongAudience));
        String expired = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),
                JwtClaimsSet.builder().issuer("issuer").audience(List.of("api")).subject("user")
                        .issuedAt(now.minusSeconds(240)).expiresAt(now.minusSeconds(120)).build())).getTokenValue();
        assertThrows(JwtException.class, () -> decoder.decode(expired));
    }
}
