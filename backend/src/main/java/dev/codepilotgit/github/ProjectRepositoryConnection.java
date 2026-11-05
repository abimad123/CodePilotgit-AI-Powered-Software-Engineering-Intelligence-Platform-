package dev.codepilotgit.github;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "project_repositories")
public class ProjectRepositoryConnection {
    @Id public UUID id;
    @Column(name = "project_id", nullable = false) public UUID projectId;
    @Column(name = "github_installation_id", nullable = false) public UUID githubInstallationId;
    @Column(name = "repository_id", nullable = false) public long repositoryId;
    @Column(name = "repository_full_name", nullable = false, length = 512) public String fullName;
    @Column(length = 2000) public String description;
    @Column(name = "default_branch", nullable = false) public String defaultBranch;
    @Column(name = "repository_private", nullable = false) public boolean repositoryPrivate;
    @Column(name = "html_url", nullable = false, length = 2000) public String htmlUrl;
    @Column(name = "created_at", nullable = false) public Instant createdAt;
    @Column(name = "updated_at", nullable = false) public Instant updatedAt;
    protected ProjectRepositoryConnection() {}
    public ProjectRepositoryConnection(UUID projectId, GithubInstallation installation, GithubClient.RepositoryInfo info) {
        this.id = UUID.randomUUID(); this.projectId = projectId; this.githubInstallationId = installation.id;
        this.repositoryId = info.id(); this.fullName = info.full_name(); this.description = info.description();
        this.defaultBranch = info.default_branch(); this.repositoryPrivate = info.isPrivate(); this.htmlUrl = info.html_url();
        this.createdAt = Instant.now(); this.updatedAt = createdAt;
    }
}
