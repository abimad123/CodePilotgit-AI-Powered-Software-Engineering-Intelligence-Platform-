package dev.codepilotgit.github;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "github_connection_states")
public class GithubConnectionState {
    @Id public UUID id;
    @Column(name = "state_hash", nullable = false, unique = true, length = 64) public String stateHash;
    @Column(name = "project_id", nullable = false) public UUID projectId;
    @Column(name = "user_id", nullable = false) public UUID userId;
    @Column(name = "intended_installation_id") public Long intendedInstallationId;
    @Column(name = "expires_at", nullable = false) public Instant expiresAt;
    @Column(name = "consumed_at") public Instant consumedAt;
    protected GithubConnectionState() {}
    public GithubConnectionState(String hash, UUID projectId, UUID userId) {
        this.id = UUID.randomUUID(); this.stateHash = hash; this.projectId = projectId;
        this.userId = userId;
        this.expiresAt = Instant.now().plusSeconds(600);
    }
}
