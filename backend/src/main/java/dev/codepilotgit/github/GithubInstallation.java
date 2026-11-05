package dev.codepilotgit.github;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "github_installations")
public class GithubInstallation {
    @Id public UUID id;
    @Column(name = "installation_id", nullable = false, unique = true) public long installationId;
    @Column(name = "account_login", nullable = false) public String accountLogin;
    @Column(name = "account_type", nullable = false, length = 50) public String accountType;
    @Column(name = "created_at", nullable = false) public Instant createdAt;
    @Column(name = "updated_at", nullable = false) public Instant updatedAt;
    protected GithubInstallation() {}
    public GithubInstallation(long installationId, String accountLogin, String accountType) {
        this.id = UUID.randomUUID(); this.installationId = installationId;
        this.accountLogin = accountLogin; this.accountType = accountType;
        this.createdAt = Instant.now(); this.updatedAt = createdAt;
    }
}
