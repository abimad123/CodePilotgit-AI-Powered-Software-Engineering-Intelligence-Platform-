package dev.codepilotgit.github;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.UUID;

@Entity @Table(name = "project_github_installations") @IdClass(ProjectGithubInstallation.Key.class)
public class ProjectGithubInstallation {
    @Id @Column(name = "project_id") public UUID projectId;
    @Id @Column(name = "github_installation_id") public UUID githubInstallationId;
    protected ProjectGithubInstallation() {}
    public ProjectGithubInstallation(UUID projectId, UUID installationId) { this.projectId = projectId; this.githubInstallationId = installationId; }
    public static class Key implements Serializable {
        public UUID projectId;
        public UUID githubInstallationId;
        public Key() {}
        @Override public boolean equals(Object o) { return o instanceof Key k && java.util.Objects.equals(projectId, k.projectId) && java.util.Objects.equals(githubInstallationId, k.githubInstallationId); }
        @Override public int hashCode() { return java.util.Objects.hash(projectId, githubInstallationId); }
    }
}
