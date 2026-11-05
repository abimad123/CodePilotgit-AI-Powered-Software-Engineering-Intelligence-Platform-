package dev.codepilotgit.github;

import dev.codepilotgit.github.GithubService.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController @RequestMapping("/api/v1/projects/{projectId}/github")
public class GithubController {
    public record SetupRequest(@NotBlank String state, Long installationId) {}
    public record CancelRequest(@NotBlank String state) {}
    public record CompleteRequest(@NotBlank String state, @NotBlank String code) {}
    public record ConnectRequest(@Positive long installationId, @Positive long repositoryId) {}
    private final GithubService service;
    public GithubController(GithubService service) { this.service = service; }
    private UUID id(JwtAuthenticationToken principal) { return UUID.fromString(principal.getName()); }
    @PostMapping("/installations/start") StartView start(JwtAuthenticationToken principal, @PathVariable UUID projectId) {
        return service.start(id(principal), projectId);
    }
    @PostMapping("/installations/setup") AuthorizationView setup(JwtAuthenticationToken principal, @PathVariable UUID projectId, @Valid @RequestBody SetupRequest body) {
        return service.setup(id(principal), projectId, body.state(), body.installationId());
    }
    @PostMapping("/installations/cancel") ResponseEntity<Void> cancel(JwtAuthenticationToken principal, @PathVariable UUID projectId, @Valid @RequestBody CancelRequest body) {
        service.cancel(id(principal), projectId, body.state());
        return ResponseEntity.noContent().build();
    }
    @PostMapping("/installations/complete") InstallationView complete(JwtAuthenticationToken principal, @PathVariable UUID projectId, @Valid @RequestBody CompleteRequest body) {
        return service.complete(id(principal), projectId, body.state(), body.code());
    }
    @GetMapping("/installations") List<InstallationView> installations(JwtAuthenticationToken principal, @PathVariable UUID projectId) {
        return service.installations(id(principal), projectId);
    }
    @GetMapping("/repositories") List<RepositoryView> repositories(JwtAuthenticationToken principal, @PathVariable UUID projectId,
            @RequestParam long installationId, @RequestParam(defaultValue="1") int page) {
        return service.list(id(principal), projectId, installationId, page);
    }
    @PostMapping("/repositories") ResponseEntity<RepositoryView> connect(JwtAuthenticationToken principal, @PathVariable UUID projectId,
            @Valid @RequestBody ConnectRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.connect(id(principal), projectId, body.installationId(), body.repositoryId()));
    }
}
