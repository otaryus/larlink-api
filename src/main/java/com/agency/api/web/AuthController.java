package com.agency.api.web;

import com.agency.api.application.accounts.AuthSession;
import com.agency.api.application.accounts.AuthWorkflowException;
import com.agency.api.application.accounts.AuthWorkflowUseCase;
import com.agency.api.service.ProfilePhotoService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthWorkflowUseCase auth;
    private final ProfilePhotoService photos;

    public AuthController(AuthWorkflowUseCase auth, ProfilePhotoService photos) {
        this.auth = auth;
        this.photos = photos;
    }

    public record Register(@NotBlank @Size(min = 4, message = "O nome deve ter pelo menos 4 caracteres.") String name,
            @Email(message = "Indique um email válido.") @NotBlank(message = "Indique o email.") String email,
            @NotBlank(message = "Seleccione o tipo de conta.") String role,
            @NotBlank @Pattern(regexp = "(?=.*\\p{L})(?=.*\\d)(?=.*[^\\p{L}\\d\\s]).{8,}",
                    message = "A palavra-passe deve ter 8 caracteres com letra, número e símbolo.") String password,
            @AssertTrue(message = "Tem de aceitar os termos de utilização.") boolean acceptTerms) { }
    public record Login(@Email @NotBlank String email, @NotBlank String password) { }
    public record AuthResponse(String token, java.util.UUID id, String name, String email, String role,
            boolean mustChangePassword) { }
    public record ChangePassword(@NotBlank @Pattern(regexp = "(?=.*\\p{L})(?=.*\\d)(?=.*[^\\p{L}\\d\\s]).{8,}")
            String password, @NotBlank String confirmation) { }

    @PostMapping(value = "/register", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> register(@Valid @RequestPart("account") Register input,
            @RequestPart(value = "photo", required = false) MultipartFile photo) {
        try {
            auth.validateRegistrationRole(input.role());
        } catch (AuthWorkflowException exception) {
            return switch (exception.kind()) {
                case INVALID -> ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
                case FORBIDDEN -> ResponseEntity.status(403).body(Map.of("error", exception.getMessage()));
                case CONFLICT -> ResponseEntity.status(409).body(Map.of("error", exception.getMessage()));
                case UNAUTHORIZED -> ResponseEntity.status(401).body(Map.of("error", exception.getMessage()));
            };
        }
        ProfilePhotoService.Photo image;
        try {
            image = photos.readValidated(photo);
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
        }
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(response(auth.register(input.name(), input.email(),
                    input.role(), input.password(), image.bytes(), image.contentType())));
        } catch (AuthWorkflowException exception) {
            return switch (exception.kind()) {
                case INVALID -> ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
                case FORBIDDEN -> ResponseEntity.status(403).body(Map.of("error", exception.getMessage()));
                case CONFLICT -> ResponseEntity.status(409).body(Map.of("error", exception.getMessage()));
                case UNAUTHORIZED -> ResponseEntity.status(401).body(Map.of("error", exception.getMessage()));
            };
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody Login input) {
        try {
            return ResponseEntity.ok(response(auth.login(input.email(), input.password())));
        } catch (AuthWorkflowException exception) {
            return ResponseEntity.status(401).body(Map.of("error", exception.getMessage()));
        }
    }

    @PostMapping("/change-password")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> changePassword(@Valid @RequestBody ChangePassword input, Authentication authentication) {
        try {
            auth.changeTemporaryPassword(authentication.getName(), input.password(), input.confirmation());
            return ResponseEntity.ok(Map.of("message", "Palavra-passe actualizada com sucesso."));
        } catch (AuthWorkflowException exception) {
            return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
        }
    }

    private AuthResponse response(AuthSession session) {
        return new AuthResponse(session.token(), session.id(), session.name(), session.email(),
                session.role(), session.mustChangePassword());
    }
}
