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
    public record StartRequest(@Positive long installationId) {}
    public record CompleteRequest(@NotBlank String state, @NotBlank String code) {}
    public record ConnectRequest(@Positive long installationId, @Positive long repositoryId) {}
    private final GithubService service;
    public GithubController(GithubService service) { this.service = service; }
    private UUID id(JwtAuthenticationToken principal) { return UUID.fromString(principal.getName()); }
    @PostMapping("/installations/start") StartView start(JwtAuthenticationToken principal, @PathVariable UUID projectId, @Valid @RequestBody StartRequest body) {
        return service.start(id(principal), projectId, body.installationId());
    }
    @PostMapping("/installations/complete") InstallationView complete(JwtAuthenticationToken principal, @PathVariable UUID projectId, @Valid @RequestBody CompleteRequest body) {
        return service.complete(id(principal), projectId, body.state(), body.code());
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
