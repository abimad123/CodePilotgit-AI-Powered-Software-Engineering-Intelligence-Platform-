package dev.codepilotgit.project;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MembershipRepository extends JpaRepository<ProjectMembership, UUID> {
    @EntityGraph(attributePaths = {"project", "project.owner"})
    Page<ProjectMembership> findByUserIdAndProjectDeletedAtIsNull(UUID userId, Pageable pageable);
    @EntityGraph(attributePaths = {"user"})
    List<ProjectMembership> findByProjectId(UUID projectId);
    Optional<ProjectMembership> findByProjectIdAndUserId(UUID projectId, UUID userId);
    boolean existsByProjectIdAndUserId(UUID projectId, UUID userId);
}
