package dev.codepilotgit.project;

import dev.codepilotgit.project.ProjectService.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController @RequestMapping("/api/v1/projects")
public class ProjectController {
    public record CreateProject(@NotBlank @Size(max=120) String name, @Size(max=1000) String description) {}
    public record UpdateProject(@Size(min=1, max=120) String name, @Size(max=1000) String description) {}
    public record AddMember(@NotNull UUID userId, @NotNull ProjectMembership.Role role) {}
    public record ProjectPage(List<ProjectView> content, int page, int size, long totalElements, int totalPages) {}
    private final ProjectService service;
    public ProjectController(ProjectService service) { this.service = service; }
    private UUID id(JwtAuthenticationToken principal) { return UUID.fromString(principal.getName()); }
    @PostMapping ResponseEntity<ProjectView> create(JwtAuthenticationToken principal, @Valid @RequestBody CreateProject body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(id(principal), body.name(), body.description()));
    }
    @GetMapping ProjectPage list(JwtAuthenticationToken principal, @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size) {
        Page<ProjectView> result = service.list(id(principal), page, size);
        return new ProjectPage(result.getContent(), page, size, result.getTotalElements(), result.getTotalPages());
    }
    @GetMapping("/{projectId}") ProjectView get(JwtAuthenticationToken principal, @PathVariable UUID projectId) { return service.get(id(principal), projectId); }
    @PatchMapping("/{projectId}") ProjectView update(JwtAuthenticationToken principal, @PathVariable UUID projectId, @Valid @RequestBody UpdateProject body) {
        return service.update(id(principal), projectId, body.name(), body.description());
    }
    @DeleteMapping("/{projectId}") ResponseEntity<Void> delete(JwtAuthenticationToken principal, @PathVariable UUID projectId) {
        service.delete(id(principal), projectId); return ResponseEntity.noContent().build();
    }
    @GetMapping("/{projectId}/members") List<MemberView> members(JwtAuthenticationToken principal, @PathVariable UUID projectId) {
        return service.members(id(principal), projectId);
    }
    @PostMapping("/{projectId}/members") ResponseEntity<MemberView> addMember(JwtAuthenticationToken principal, @PathVariable UUID projectId, @Valid @RequestBody AddMember body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.addMember(id(principal), projectId, body.userId(), body.role()));
    }
    @DeleteMapping("/{projectId}/members/{userId}") ResponseEntity<Void> removeMember(JwtAuthenticationToken principal, @PathVariable UUID projectId, @PathVariable UUID userId) {
        service.removeMember(id(principal), projectId, userId); return ResponseEntity.noContent().build();
    }
}
