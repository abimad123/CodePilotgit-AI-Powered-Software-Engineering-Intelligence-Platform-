package dev.codepilotgit.github;

import dev.codepilotgit.common.ApiException;
import dev.codepilotgit.project.ProjectMembership;
import dev.codepilotgit.project.ProjectService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GithubServiceTest {
    @Mock ProjectService projects;
    @Mock GithubClient client;
    @Mock GithubConnectionStateRepository states;
    @Mock GithubInstallationRepository installations;
    @Mock ProjectGithubInstallationRepository links;
    @Mock ProjectRepositoryConnectionRepository repositories;
    GithubService service;
    UUID actor = UUID.randomUUID(), project = UUID.randomUUID();
    @BeforeEach void setUp() {
        service = new GithubService(projects, client, states, installations, links, repositories,
                "client-id", "http://localhost:5173/github/callback", "https://github.com");
    }
    @Test void stateIsRandomAndBoundToActorProjectAndInstallation() {
        when(client.configured()).thenReturn(true);
        AtomicReference<GithubConnectionState> saved = new AtomicReference<>();
        when(states.save(any())).thenAnswer(inv -> { saved.set(inv.getArgument(0)); return inv.getArgument(0); });
        var first = service.start(actor, project, 42);
        var second = service.start(actor, project, 42);
        assertNotEquals(first.authorizationUrl(), second.authorizationUrl());
        assertEquals(actor, saved.get().userId);
        assertEquals(project, saved.get().projectId);
        assertEquals(42, saved.get().intendedInstallationId);
    }
    @Test void expiredAndConsumedStateAreRejectedBeforeGitHubCall() {
        GithubConnectionState state = new GithubConnectionState("hash", project, actor, 42);
        state.expiresAt = Instant.now().minusSeconds(1);
        when(states.findByStateHash(anyString())).thenReturn(Optional.of(state));
        assertThrows(ApiException.class, () -> service.complete(actor, project, "a".repeat(43), "code"));
        state.expiresAt = Instant.now().plusSeconds(60); state.consumedAt = Instant.now();
        assertThrows(ApiException.class, () -> service.complete(actor, project, "a".repeat(43), "code"));
        verifyNoInteractions(client);
    }
    @Test void cannotAssociateInstallationWithoutUserAuthorization() {
        GithubConnectionState state = new GithubConnectionState("hash", project, actor, 42);
        when(states.findByStateHash(anyString())).thenReturn(Optional.of(state));
        when(client.userToken("code")).thenReturn("user-token");
        when(client.userCanAccessInstallation("user-token", 42)).thenReturn(false);
        assertThrows(ApiException.class, () -> service.complete(actor, project, "a".repeat(43), "code"));
        verifyNoInteractions(installations, links);
    }
    @Test void connectionRequiresProjectRole() {
        doThrow(new ApiException(org.springframework.http.HttpStatus.FORBIDDEN, "FORBIDDEN", "denied"))
                .when(projects).requireRole(actor, project, ProjectMembership.Role.OWNER, ProjectMembership.Role.ADMIN);
        assertThrows(ApiException.class, () -> service.start(actor, project, 42));
        verifyNoInteractions(client, states);
    }
}
