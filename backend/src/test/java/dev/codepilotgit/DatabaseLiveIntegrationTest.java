package dev.codepilotgit;

import dev.codepilotgit.auth.AuthService;
import dev.codepilotgit.project.ProjectService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** Runs against an explicitly supplied, disposable PostgreSQL development database. */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "DATABASE_URL", matches = "jdbc:postgresql:.*")
class DatabaseLiveIntegrationTest {
    @Autowired AuthService auth;
    @Autowired ProjectService projects;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entityManager;

    @Test @Transactional
    void refreshAndProjectRelationshipsWorkAcrossPersistenceBoundary() {
        String email = "live-" + UUID.randomUUID() + "@example.test";
        var user = auth.register("Live DB", email, "long-password-123");
        var first = auth.login(email, "long-password-123");
        var next = auth.refresh(first.refreshToken());
        assertNotEquals(first.refreshToken(), next.refreshToken());
        var project = projects.create(user.id(), "Live DB project", null);
        entityManager.flush(); entityManager.clear();
        assertEquals(user.id(), projects.get(user.id(), project.id()).ownerId());
        assertEquals(1, projects.list(user.id(), 0, 20).getTotalElements());
    }

    @Test @Transactional
    void uniqueMembershipConstraintIsEnforced() {
        String email = "unique-" + UUID.randomUUID() + "@example.test";
        var user = auth.register("Unique DB", email, "long-password-123");
        var project = projects.create(user.id(), "Unique project", null);
        entityManager.flush();
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO project_memberships (id, project_id, user_id, role, joined_at) VALUES (?, ?, ?, 'MEMBER', now())",
                UUID.randomUUID(), project.id(), user.id()));
    }

    @Test @Transactional
    void unknownUserForeignKeyIsEnforced() {
        String email = "foreign-" + UUID.randomUUID() + "@example.test";
        var user = auth.register("FK DB", email, "long-password-123");
        var project = projects.create(user.id(), "FK project", null);
        entityManager.flush();
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO project_memberships (id, project_id, user_id, role, joined_at) VALUES (?, ?, ?, 'MEMBER', now())",
                UUID.randomUUID(), project.id(), UUID.randomUUID()));
    }
}
