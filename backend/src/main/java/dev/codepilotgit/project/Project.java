package dev.codepilotgit.project;

import dev.codepilotgit.user.User;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "projects")
public class Project {
    @Id public UUID id;
    @Column(nullable = false, length = 120) public String name;
    @Column(length = 1000) public String description;
    @Column(nullable = false, unique = true, length = 140) public String slug;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "owner_id") public User owner;
    @Column(name = "created_at", nullable = false) public Instant createdAt;
    @Column(name = "updated_at", nullable = false) public Instant updatedAt;
    @Column(name = "deleted_at") public Instant deletedAt;
    protected Project() {}
    public Project(String name, String description, String slug, User owner) {
        this.id = UUID.randomUUID(); this.name = name; this.description = description; this.slug = slug;
        this.owner = owner; this.createdAt = Instant.now(); this.updatedAt = createdAt;
    }
    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getSlug() { return slug; }
    public User getOwner() { return owner; }
    public Instant getCreatedAt() { return createdAt; }
    public void updateDetails(String name, String description) {
        if (name != null) this.name = name.trim();
        if (description != null) this.description = description;
        this.updatedAt = Instant.now();
    }
    public void softDelete() { this.deletedAt = Instant.now(); this.updatedAt = deletedAt; }
}
