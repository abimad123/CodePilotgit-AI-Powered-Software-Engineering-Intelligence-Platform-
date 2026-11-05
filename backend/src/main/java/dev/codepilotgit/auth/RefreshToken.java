package dev.codepilotgit.auth;

import dev.codepilotgit.user.User;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "refresh_tokens")
public class RefreshToken {
    @Id public UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id") public User user;
    @Column(name = "token_hash", nullable = false, unique = true, length = 64) public String tokenHash;
    @Column(name = "expires_at", nullable = false) public Instant expiresAt;
    @Column(name = "revoked_at") public Instant revokedAt;
    @Column(name = "created_at", nullable = false) public Instant createdAt;
    @Column(name = "replaced_by_token_id") public UUID replacedByTokenId;
    protected RefreshToken() {}
    public RefreshToken(User user, String hash, Instant expiresAt) {
        this.id = UUID.randomUUID(); this.user = user; this.tokenHash = hash;
        this.expiresAt = expiresAt; this.createdAt = Instant.now();
    }
}
