package dev.codepilotgit.github;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface ProjectRepositoryConnectionRepository extends JpaRepository<ProjectRepositoryConnection, UUID> {
    boolean existsByProjectIdAndRepositoryId(UUID projectId, long repositoryId);
}
