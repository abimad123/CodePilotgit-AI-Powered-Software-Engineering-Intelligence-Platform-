package dev.codepilotgit.project;

import dev.codepilotgit.user.User;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "project_memberships")
public class ProjectMembership {
    public enum Role { OWNER, ADMIN, MEMBER }
    @Id public UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "project_id") public Project project;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id") public User user;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) public Role role;
    @Column(name = "joined_at", nullable = false) public Instant joinedAt;
    protected ProjectMembership() {}
    public ProjectMembership(Project project, User user, Role role) {
        this.id = UUID.randomUUID(); this.project = project; this.user = user;
        this.role = role; this.joinedAt = Instant.now();
    }
}
