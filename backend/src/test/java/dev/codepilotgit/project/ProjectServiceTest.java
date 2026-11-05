package dev.codepilotgit.project;

import dev.codepilotgit.common.ApiException;
import dev.codepilotgit.user.User;
import dev.codepilotgit.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {
    @Mock ProjectRepository projects;
    @Mock MembershipRepository memberships;
    @Mock UserRepository users;
    ProjectService service;
    User owner = new User("Owner", "owner@example.com", "hash");
    @BeforeEach void setUp() { service = new ProjectService(projects, memberships, users); }
    @Test void creationAddsOwnerMembership() {
        when(users.findById(owner.id)).thenReturn(Optional.of(owner));
        when(projects.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));
        var view = service.create(owner.id, "My project", "description");
        assertEquals(owner.id, view.ownerId());
        verify(memberships).save(argThat(m -> m.role == ProjectMembership.Role.OWNER && m.project.id.equals(view.id())));
    }
    @Test void unrelatedUserCannotReadProject() {
        Project project = new Project("P", null, "p", owner);
        when(projects.findByIdAndDeletedAtIsNull(project.id)).thenReturn(Optional.of(project));
        assertThrows(ApiException.class, () -> service.get(java.util.UUID.randomUUID(), project.id));
    }
    @Test void adminCannotAddAnotherAdmin() {
        Project project = new Project("P", null, "p", owner);
        User admin = new User("Admin", "admin@example.com", "hash");
        when(projects.findByIdAndDeletedAtIsNull(project.id)).thenReturn(Optional.of(project));
        when(memberships.findByProjectIdAndUserId(project.id, admin.id)).thenReturn(Optional.of(new ProjectMembership(project, admin, ProjectMembership.Role.ADMIN)));
        assertThrows(ApiException.class, () -> service.addMember(admin.id, project.id, java.util.UUID.randomUUID(), ProjectMembership.Role.ADMIN));
        verify(users, never()).findById(any());
    }
    @Test void ownerCannotBeRemoved() {
        Project project = new Project("P", null, "p", owner);
        ProjectMembership membership = new ProjectMembership(project, owner, ProjectMembership.Role.OWNER);
        when(projects.findByIdAndDeletedAtIsNull(project.id)).thenReturn(Optional.of(project));
        when(memberships.findByProjectIdAndUserId(project.id, owner.id)).thenReturn(Optional.of(membership));
        assertThrows(ApiException.class, () -> service.removeMember(owner.id, project.id, owner.id));
        verify(memberships, never()).delete(any());
    }
}
