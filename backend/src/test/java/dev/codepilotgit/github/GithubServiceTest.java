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
                "client-id", "codepilotgit-test", "http://localhost:5173/github/callback", "https://github.com");
    }
    @Test void stateIsRandomAndBoundToActorAndProject() {
        when(client.configured()).thenReturn(true);
        AtomicReference<GithubConnectionState> saved = new AtomicReference<>();
        when(states.save(any())).thenAnswer(inv -> { saved.set(inv.getArgument(0)); return inv.getArgument(0); });
        var first = service.start(actor, project);
        var second = service.start(actor, project);
        assertNotEquals(first.installationUrl(), second.installationUrl());
        assertTrue(first.installationUrl().startsWith("https://github.com/apps/codepilotgit-test/installations/new?state="));
        assertEquals(actor, saved.get().userId);
        assertEquals(project, saved.get().projectId);
        assertNull(saved.get().intendedInstallationId);
    }
    @Test void expiredAndConsumedStateAreRejectedBeforeGitHubCall() {
        GithubConnectionState state = new GithubConnectionState("hash", project, actor);
        state.intendedInstallationId = 42L;
        state.expiresAt = Instant.now().minusSeconds(1);
        when(states.findByStateHash(anyString())).thenReturn(Optional.of(state));
        assertThrows(ApiException.class, () -> service.complete(actor, project, "a".repeat(43), "code"));
        state.expiresAt = Instant.now().plusSeconds(60); state.consumedAt = Instant.now();
        assertThrows(ApiException.class, () -> service.complete(actor, project, "a".repeat(43), "code"));
        verifyNoInteractions(client);
    }
    @Test void cannotAssociateInstallationWithoutUserAuthorization() {
        GithubConnectionState state = new GithubConnectionState("hash", project, actor);
        state.intendedInstallationId = 42L;
        when(states.findByStateHash(anyString())).thenReturn(Optional.of(state));
        when(client.userToken("code")).thenReturn("user-token");
        when(client.userCanAccessInstallation("user-token", 42)).thenReturn(false);
        assertThrows(ApiException.class, () -> service.complete(actor, project, "a".repeat(43), "code"));
        verifyNoInteractions(installations, links);
    }
    @Test void connectionRequiresProjectRole() {
        doThrow(new ApiException(org.springframework.http.HttpStatus.FORBIDDEN, "FORBIDDEN", "denied"))
                .when(projects).requireRole(actor, project, ProjectMembership.Role.OWNER, ProjectMembership.Role.ADMIN);
        assertThrows(ApiException.class, () -> service.start(actor, project));
        verifyNoInteractions(client, states);
    }
    private GithubConnectionState pending() {
        GithubConnectionState state = new GithubConnectionState("hash", project, actor);
        when(states.findByStateHash(anyString())).thenReturn(Optional.of(state));
        return state;
    }
    @Test void validInstallationHandoffAssociatesOnlyInitiatingProject() {
        GithubConnectionState state = pending();
        String raw = "a".repeat(43);
        var authorization = service.setup(actor, project, raw, 42L);
        assertTrue(authorization.authorizationUrl().contains("/login/oauth/authorize"));
        assertTrue(authorization.authorizationUrl().contains("state=" + raw));
        assertEquals(42L, state.intendedInstallationId);
        when(client.userToken("code")).thenReturn("user-token");
        when(client.userCanAccessInstallation("user-token", 42)).thenReturn(true);
        when(client.installation(42)).thenReturn(new GithubClient.InstallationInfo(42, "team", "Organization"));
        when(installations.findByInstallationId(42)).thenReturn(Optional.empty());
        when(installations.save(any())).thenAnswer(inv -> inv.getArgument(0));
        var result = service.complete(actor, project, raw, "code");
        assertEquals(42, result.installationId());
        assertNotNull(state.consumedAt);
        verify(links).save(argThat(link -> link.projectId.equals(project)));
        verify(links, never()).save(argThat(link -> !link.projectId.equals(project)));
        assertEquals("INVALID_STATE", assertThrows(ApiException.class, () -> service.complete(actor, project, raw, "code")).code);
    }
    @Test void invalidStateIsRejected() {
        assertEquals("INVALID_STATE", assertThrows(ApiException.class,
                () -> service.setup(actor, project, "invalid", 42L)).code);
        verifyNoInteractions(client, links);
    }
    @Test void setupRejectsMissingInstallationIdAndReplay() {
        GithubConnectionState state = pending();
        assertEquals("MISSING_INSTALLATION_ID", assertThrows(ApiException.class,
                () -> service.setup(actor, project, "a".repeat(43), null)).code);
        assertNull(state.intendedInstallationId);
        service.setup(actor, project, "a".repeat(43), 42L);
        assertEquals("INVALID_STATE", assertThrows(ApiException.class,
                () -> service.setup(actor, project, "a".repeat(43), 43L)).code);
    }
    @Test void cancelledInstallationConsumesState() {
        GithubConnectionState state = pending();
        service.cancel(actor, project, "a".repeat(43));
        assertNotNull(state.consumedAt);
        assertEquals("INVALID_STATE", assertThrows(ApiException.class,
                () -> service.setup(actor, project, "a".repeat(43), 42L)).code);
    }
    @Test void setupAndCallbackRejectAnotherUserOrProject() {
        pending();
        assertEquals("INVALID_STATE", assertThrows(ApiException.class,
                () -> service.setup(UUID.randomUUID(), project, "a".repeat(43), 42L)).code);
        assertEquals("INVALID_STATE", assertThrows(ApiException.class,
                () -> service.setup(actor, UUID.randomUUID(), "a".repeat(43), 42L)).code);
        assertEquals("INVALID_STATE", assertThrows(ApiException.class,
                () -> service.complete(UUID.randomUUID(), project, "a".repeat(43), "code")).code);
        verifyNoInteractions(client, links);
    }
    @Test void callbackRechecksProjectPermissions() {
        GithubConnectionState state = pending(); state.intendedInstallationId = 42L;
        doThrow(new ApiException(org.springframework.http.HttpStatus.FORBIDDEN, "FORBIDDEN", "denied"))
                .when(projects).requireRole(actor, project, ProjectMembership.Role.OWNER, ProjectMembership.Role.ADMIN);
        assertEquals("FORBIDDEN", assertThrows(ApiException.class,
                () -> service.complete(actor, project, "a".repeat(43), "code")).code);
        verifyNoInteractions(client, links);
    }
    @Test void installationVerificationFailureLeavesStateUnconsumed() {
        GithubConnectionState state = pending(); state.intendedInstallationId = 42L;
        when(client.userToken("code")).thenReturn("user-token");
        when(client.userCanAccessInstallation("user-token", 42)).thenReturn(true);
        when(client.installation(42)).thenReturn(new GithubClient.InstallationInfo(99, "wrong", "User"));
        assertEquals("UPSTREAM_FAILURE", assertThrows(ApiException.class,
                () -> service.complete(actor, project, "a".repeat(43), "code")).code);
        assertNull(state.consumedAt);
        verifyNoInteractions(links);
    }
    @Test void upstreamTimeoutDoesNotLinkInstallation() {
        GithubConnectionState state = pending(); state.intendedInstallationId = 42L;
        when(client.userToken("code")).thenThrow(new ApiException(org.springframework.http.HttpStatus.GATEWAY_TIMEOUT,
                "UPSTREAM_TIMEOUT", "GitHub did not respond in time."));
        assertEquals("UPSTREAM_TIMEOUT", assertThrows(ApiException.class,
                () -> service.complete(actor, project, "a".repeat(43), "code")).code);
        assertNull(state.consumedAt);
        verifyNoInteractions(links);
    }
    @Test void installationStatusRequiresProjectMembership() {
        doThrow(new ApiException(org.springframework.http.HttpStatus.NOT_FOUND, "NOT_FOUND", "hidden"))
                .when(projects).requireMember(actor, project);
        assertEquals("NOT_FOUND", assertThrows(ApiException.class,
                () -> service.installations(actor, project)).code);
        verifyNoInteractions(links, installations);
    }
}
