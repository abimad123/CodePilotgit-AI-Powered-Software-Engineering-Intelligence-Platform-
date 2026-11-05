package dev.codepilotgit.github;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface ProjectGithubInstallationRepository extends JpaRepository<ProjectGithubInstallation, ProjectGithubInstallation.Key> {
    List<ProjectGithubInstallation> findByProjectId(UUID projectId);
}
