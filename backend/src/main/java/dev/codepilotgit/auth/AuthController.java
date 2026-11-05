package dev.codepilotgit.auth;

import dev.codepilotgit.auth.AuthService.TokenView;
import dev.codepilotgit.auth.AuthService.UserView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
public class AuthController {
    public record RegisterRequest(@NotBlank @Size(max=120) String name, @NotBlank @Email @Size(max=320) String email,
                                  @NotBlank @Size(min=12, max=72) String password) {}
    public record LoginRequest(@NotBlank String email, @NotBlank String password) {}
    public record RefreshRequest(@NotBlank String refreshToken) {}
    private final AuthService service;
    public AuthController(AuthService service) { this.service = service; }
    @PostMapping("/api/v1/auth/register")
    ResponseEntity<UserView> register(@Valid @RequestBody RegisterRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.register(body.name(), body.email(), body.password()));
    }
    @PostMapping("/api/v1/auth/login") TokenView login(@Valid @RequestBody LoginRequest body) { return service.login(body.email(), body.password()); }
    @PostMapping("/api/v1/auth/refresh") TokenView refresh(@Valid @RequestBody RefreshRequest body) { return service.refresh(body.refreshToken()); }
    @PostMapping("/api/v1/auth/logout") ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest body) {
        service.logout(body.refreshToken()); return ResponseEntity.noContent().build();
    }
    @GetMapping("/api/v1/users/me") UserView me(JwtAuthenticationToken principal) {
        return service.me(UUID.fromString(principal.getName()));
    }
}
