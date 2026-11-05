package dev.codepilotgit;

import dev.codepilotgit.auth.AuthService;
import dev.codepilotgit.project.ProjectService;
import dev.codepilotgit.project.MembershipRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import java.util.Base64;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
class DatabaseIntegrationTest {
    @Container static PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:0.8.7-pg18-trixie").asCompatibleSubstituteFor("postgres"));
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("app.jwt.secret", () -> Base64.getEncoder().encodeToString(new byte[32]));
    }
    @Autowired AuthService auth;
    @Autowired ProjectService projects;
    @Autowired MembershipRepository memberships;
    @Autowired JdbcTemplate jdbc;
    @Test void migrationsValidateAndProjectMembershipPersists() {
        var user = auth.register("DB Test", "db-test@example.com", "long-password-123");
        var tokens = auth.login("db-test@example.com", "long-password-123");
        var rotated = auth.refresh(tokens.refreshToken());
        assertNotEquals(tokens.refreshToken(), rotated.refreshToken());
        var project = projects.create(user.id(), "Migration test", null);
        assertEquals("OWNER", project.role());
        assertTrue(memberships.existsByProjectIdAndUserId(project.id(), user.id()));
        assertEquals(1, projects.list(user.id(), 0, 20).getTotalElements());
    }
    @Test void databaseRejectsDuplicateMembershipAndUnknownUser() {
        var user = auth.register("Constraint Test", "db-constraint@example.com", "long-password-123");
        var project = projects.create(user.id(), "Constraint test", null);
        String sql = "INSERT INTO project_memberships (id, project_id, user_id, role, joined_at) VALUES (?, ?, ?, 'MEMBER', now())";
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update(sql, java.util.UUID.randomUUID(), project.id(), user.id()));
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update(sql, java.util.UUID.randomUUID(), project.id(), java.util.UUID.randomUUID()));
    }
}
