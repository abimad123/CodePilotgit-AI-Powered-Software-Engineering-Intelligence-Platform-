package dev.codepilotgit.github;

import dev.codepilotgit.common.ApiException;
import dev.codepilotgit.project.ProjectMembership;
import dev.codepilotgit.project.ProjectService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;

@Service
public class GithubService {
    public record StartView(String authorizationUrl) {}
    public record InstallationView(long installationId, String accountLogin, String accountType) {}
    public record RepositoryView(long repositoryId, String fullName, String description, String defaultBranch, boolean privateRepository, String htmlUrl) {}
    private final ProjectService projects;
    private final GithubClient client;
    private final GithubConnectionStateRepository states;
    private final GithubInstallationRepository installations;
    private final ProjectGithubInstallationRepository links;
    private final ProjectRepositoryConnectionRepository repositories;
    private final SecureRandom random = new SecureRandom();
    private final String clientId, redirectUri, oauthBase;
    public GithubService(ProjectService projects, GithubClient client, GithubConnectionStateRepository states,
                         GithubInstallationRepository installations, ProjectGithubInstallationRepository links,
                         ProjectRepositoryConnectionRepository repositories,
                         @Value("${app.github.client-id:}") String clientId,
                         @Value("${app.github.redirect-uri:}") String redirectUri,
                         @Value("${app.github.oauth-base-url:https://github.com}") String oauthBase) {
        this.projects = projects; this.client = client; this.states = states; this.installations = installations;
        this.links = links; this.repositories = repositories;
        this.clientId = clientId; this.redirectUri = redirectUri; this.oauthBase = oauthBase;
    }
    @Transactional
    public StartView start(UUID actor, UUID projectId, long installationId) {
        projects.requireRole(actor, projectId, ProjectMembership.Role.OWNER, ProjectMembership.Role.ADMIN);
        if (!client.configured() || clientId.isBlank() || redirectUri.isBlank()) throw unavailable();
        if (installationId <= 0) throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Invalid installation ID.");
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        states.save(new GithubConnectionState(hash(raw), projectId, actor, installationId));
        String url = UriComponentsBuilder.fromUriString(oauthBase).path("/login/oauth/authorize")
                .queryParam("client_id", clientId).queryParam("redirect_uri", redirectUri)
                .queryParam("state", raw).build().encode().toUriString();
        return new StartView(url);
    }
    @Transactional
    public InstallationView complete(UUID actor, UUID projectId, String state, String code) {
        GithubConnectionState attempt = states.findByStateHash(hash(state))
                .orElseThrow(GithubService::invalidState);
        if (attempt.consumedAt != null || !attempt.expiresAt.isAfter(Instant.now()) || !attempt.userId.equals(actor)
                || !attempt.projectId.equals(projectId)) throw invalidState();
        projects.requireRole(actor, attempt.projectId, ProjectMembership.Role.OWNER, ProjectMembership.Role.ADMIN);
        String userToken = client.userToken(code);
        if (!client.userCanAccessInstallation(userToken, attempt.intendedInstallationId))
            throw new ApiException(HttpStatus.FORBIDDEN, "GITHUB_INSTALLATION_DENIED", "GitHub user cannot access this installation.");
        GithubClient.InstallationInfo info = client.installation(attempt.intendedInstallationId);
        GithubInstallation installation = installations.findByInstallationId(info.id()).orElseGet(() ->
                installations.save(new GithubInstallation(info.id(), info.accountLogin(), info.accountType())));
        ProjectGithubInstallation.Key key = new ProjectGithubInstallation.Key();
        key.projectId = attempt.projectId; key.githubInstallationId = installation.id;
        if (!links.existsById(key)) links.save(new ProjectGithubInstallation(attempt.projectId, installation.id));
        attempt.consumedAt = Instant.now();
        return new InstallationView(info.id(), info.accountLogin(), info.accountType());
    }
    @Transactional(readOnly = true)
    public List<RepositoryView> list(UUID actor, UUID projectId, long installationId, int page) {
        GithubInstallation installation = requireInstallation(actor, projectId, installationId);
        return client.repositories(installation.installationId, page).stream().map(this::view).toList();
    }
    @Transactional
    public RepositoryView connect(UUID actor, UUID projectId, long installationId, long repositoryId) {
        projects.requireRole(actor, projectId, ProjectMembership.Role.OWNER, ProjectMembership.Role.ADMIN);
        GithubInstallation installation = requireInstallation(actor, projectId, installationId);
        if (repositories.existsByProjectIdAndRepositoryId(projectId, repositoryId))
            throw new ApiException(HttpStatus.CONFLICT, "REPOSITORY_ALREADY_CONNECTED", "Repository is already connected.");
        GithubClient.RepositoryInfo info = client.repository(installationId, repositoryId);
        repositories.save(new ProjectRepositoryConnection(projectId, installation, info));
        return view(info);
    }
    private GithubInstallation requireInstallation(UUID actor, UUID projectId, long installationId) {
        projects.requireMember(actor, projectId);
        GithubInstallation installation = installations.findByInstallationId(installationId)
                .orElseThrow(GithubService::notFound);
        ProjectGithubInstallation.Key key = new ProjectGithubInstallation.Key();
        key.projectId = projectId; key.githubInstallationId = installation.id;
        if (!links.existsById(key)) throw notFound();
        return installation;
    }
    private RepositoryView view(GithubClient.RepositoryInfo info) {
        return new RepositoryView(info.id(), info.full_name(), info.description(), info.default_branch(), info.isPrivate(), info.html_url());
    }
    private String hash(String raw) {
        if (raw == null || raw.length() != 43 || !raw.matches("[A-Za-z0-9_-]+")) throw invalidState();
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.US_ASCII))); }
        catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
    private static ApiException invalidState() { return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_STATE", "GitHub connection state is invalid or expired."); }
    private static ApiException notFound() { return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "GitHub installation not connected to project."); }
    private static ApiException unavailable() { return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "GITHUB_NOT_CONFIGURED", "GitHub App is not configured."); }
}
