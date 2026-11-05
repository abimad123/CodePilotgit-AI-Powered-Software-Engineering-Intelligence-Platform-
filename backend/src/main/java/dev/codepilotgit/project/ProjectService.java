package dev.codepilotgit.project;

import dev.codepilotgit.common.ApiException;
import dev.codepilotgit.user.User;
import dev.codepilotgit.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ProjectService {
    public record ProjectView(UUID id, String name, String description, String slug, UUID ownerId, String role, Instant createdAt) {}
    public record MemberView(UUID userId, String name, String email, String role, Instant joinedAt) {}
    private final ProjectRepository projects;
    private final MembershipRepository memberships;
    private final UserRepository users;
    public ProjectService(ProjectRepository projects, MembershipRepository memberships, UserRepository users) {
        this.projects = projects; this.memberships = memberships; this.users = users;
    }
    @Transactional
    public ProjectView create(UUID actor, String name, String description) {
        User owner = users.findById(actor).orElseThrow(ProjectService::notFound);
        String slug = name.trim().toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        if (slug.isBlank()) slug = "project";
        slug = (slug.length() > 100 ? slug.substring(0, 100) : slug) + "-" + UUID.randomUUID().toString().substring(0, 8);
        Project project = projects.save(new Project(name.trim(), description, slug, owner));
        memberships.save(new ProjectMembership(project, owner, ProjectMembership.Role.OWNER));
        return view(project, ProjectMembership.Role.OWNER);
    }
    @Transactional(readOnly = true)
    public Page<ProjectView> list(UUID actor, int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Invalid pagination.");
        return memberships.findByUserIdAndProjectDeletedAtIsNull(actor, PageRequest.of(page, size, Sort.by("joinedAt").descending()))
                .map(m -> view(m.project, m.role));
    }
    @Transactional(readOnly = true)
    public ProjectView get(UUID actor, UUID id) {
        ProjectMembership member = requireMember(actor, id);
        return view(member.project, member.role);
    }
    @Transactional
    public ProjectView update(UUID actor, UUID id, String name, String description) {
        ProjectMembership member = requireRole(actor, id, ProjectMembership.Role.OWNER, ProjectMembership.Role.ADMIN);
        if (name != null && name.isBlank()) throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Project name must not be blank.");
        member.project.updateDetails(name, description);
        return view(member.project, member.role);
    }
    @Transactional
    public void delete(UUID actor, UUID id) {
        ProjectMembership member = requireRole(actor, id, ProjectMembership.Role.OWNER);
        member.project.softDelete();
    }
    @Transactional(readOnly = true)
    public List<MemberView> members(UUID actor, UUID id) {
        requireMember(actor, id);
        return memberships.findByProjectId(id).stream().map(m -> new MemberView(m.user.getId(), m.user.getName(), m.user.getEmail(), m.role.name(), m.joinedAt)).toList();
    }
    @Transactional
    public MemberView addMember(UUID actor, UUID id, UUID targetId, ProjectMembership.Role role) {
        ProjectMembership manager = requireRole(actor, id, ProjectMembership.Role.OWNER, ProjectMembership.Role.ADMIN);
        if (role == ProjectMembership.Role.OWNER || (role == ProjectMembership.Role.ADMIN && manager.role != ProjectMembership.Role.OWNER)
                || actor.equals(targetId)) throw forbidden();
        if (memberships.existsByProjectIdAndUserId(id, targetId)) throw new ApiException(HttpStatus.CONFLICT, "ALREADY_MEMBER", "User is already a member.");
        User target = users.findById(targetId).orElseThrow(ProjectService::notFound);
        ProjectMembership member = memberships.save(new ProjectMembership(manager.project, target, role));
        return new MemberView(target.id, target.name, target.email, role.name(), member.joinedAt);
    }
    @Transactional
    public void removeMember(UUID actor, UUID id, UUID targetId) {
        ProjectMembership manager = requireRole(actor, id, ProjectMembership.Role.OWNER, ProjectMembership.Role.ADMIN);
        ProjectMembership target = memberships.findByProjectIdAndUserId(id, targetId).orElseThrow(ProjectService::notFound);
        if (target.role == ProjectMembership.Role.OWNER || (target.role == ProjectMembership.Role.ADMIN && manager.role != ProjectMembership.Role.OWNER)) throw forbidden();
        memberships.delete(target);
    }
    public ProjectMembership requireMember(UUID actor, UUID id) {
        Project project = projects.findByIdAndDeletedAtIsNull(id).orElseThrow(ProjectService::notFound);
        return memberships.findByProjectIdAndUserId(project.id, actor).orElseThrow(ProjectService::notFound);
    }
    public ProjectMembership requireRole(UUID actor, UUID id, ProjectMembership.Role... roles) {
        ProjectMembership member = requireMember(actor, id);
        if (java.util.Arrays.stream(roles).noneMatch(r -> r == member.role)) throw forbidden();
        return member;
    }
    private ProjectView view(Project p, ProjectMembership.Role role) {
        return new ProjectView(p.getId(), p.getName(), p.getDescription(), p.getSlug(), p.getOwner().getId(), role.name(), p.getCreatedAt());
    }
    private static ApiException notFound() { return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Project or user not found."); }
    private static ApiException forbidden() { return new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Insufficient project role."); }
}
