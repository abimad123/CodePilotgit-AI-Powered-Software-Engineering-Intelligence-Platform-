package dev.codepilotgit.user;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "users")
public class User {
    @Id public UUID id;
    @Column(nullable = false, length = 120) public String name;
    @Column(nullable = false, unique = true, length = 320) public String email;
    @Column(name = "password_hash", nullable = false, length = 100) public String passwordHash;
    @Column(nullable = false, length = 20) public String status;
    @Column(name = "created_at", nullable = false) public Instant createdAt;
    @Column(name = "updated_at", nullable = false) public Instant updatedAt;
    protected User() {}
    public User(String name, String email, String passwordHash) {
        this.id = UUID.randomUUID(); this.name = name; this.email = email; this.passwordHash = passwordHash;
        this.status = "ACTIVE"; this.createdAt = Instant.now(); this.updatedAt = createdAt;
    }
    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getStatus() { return status; }
}
