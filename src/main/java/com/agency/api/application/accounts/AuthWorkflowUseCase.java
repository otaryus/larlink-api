package com.agency.api.application.accounts;

import com.agency.api.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Locale;

@Service
public class AuthWorkflowUseCase {
    private final AuthPort auth;
    public AuthWorkflowUseCase(AuthPort auth) { this.auth = auth; }

    public void validateRegistrationRole(String requestedRole) {
        Role role = registrationRole(requestedRole);
        if (role == Role.HOUSEKEEPER)
            throw new AuthWorkflowException(AuthWorkflowException.Kind.INVALID,
                    "Crie a sua conta como candidata. O perfil de empregada é atribuído depois de iniciar o primeiro trabalho.");
        if (role != Role.CLIENT && role != Role.CANDIDATE)
            throw new AuthWorkflowException(AuthWorkflowException.Kind.FORBIDDEN,
                    "Este tipo de conta é criado pela agência.");
    }

    @Transactional(noRollbackFor = AuthWorkflowException.class)
    public AuthSession register(String name, String email, String requestedRole, String password,
            byte[] photo, String contentType) {
        Role role = registrationRole(requestedRole);
        validateRegistrationRole(requestedRole);
        if (auth.byEmail(email).isPresent())
            throw new AuthWorkflowException(AuthWorkflowException.Kind.CONFLICT,
                    "Este email já está registado.");
        if (photo == null)
            throw new AuthWorkflowException(AuthWorkflowException.Kind.INVALID,
                    "Carregue uma fotografia de perfil.");
        Account account = new Account(name.trim(), email, auth.encode(password), role);
        account.setProfilePhoto(photo, contentType);
        account = auth.save(account);
        auth.record(account, "ACCOUNT_REGISTERED", "Account", account.getId(), role.name());
        auth.notifyRole(Role.AGENT, role == Role.CANDIDATE ? "NEW_CANDIDATE" : "NEW_CLIENT",
                role == Role.CANDIDATE ? "Nova candidata registada e aguardando revisão."
                        : "Novo cliente registado.", "Account", account.getId());
        return session(account);
    }

    private Role registrationRole(String requestedRole) {
        try {
            String requested = requestedRole.toUpperCase(Locale.ROOT);
            return switch (requested) {
                case "AGENCY", "AGENTE" -> Role.AGENT;
                case "HOUSEKEEPER", "EMPREGADA" -> Role.HOUSEKEEPER;
                case "CANDIDATE", "CANDIDATA" -> Role.CANDIDATE;
                case "CLIENT", "CLIENTE" -> Role.CLIENT;
                default -> Role.valueOf(requested);
            };
        } catch (Exception exception) {
            throw new AuthWorkflowException(AuthWorkflowException.Kind.INVALID, "Perfil inválido.");
        }
    }

    @Transactional(readOnly = true)
    public AuthSession login(String email, String password) {
        Account account = auth.byEmail(email).orElse(null);
        if (account == null || !auth.matches(password, account.getPasswordHash()))
            throw new AuthWorkflowException(AuthWorkflowException.Kind.UNAUTHORIZED,
                    "Email ou palavra-passe inválidos.");
        return session(account);
    }

    @Transactional(noRollbackFor = AuthWorkflowException.class)
    public void changeTemporaryPassword(String email, String password, String confirmation) {
        if (!password.equals(confirmation))
            throw new AuthWorkflowException(AuthWorkflowException.Kind.INVALID,
                    "As palavras-passe não coincidem.");
        Account account = auth.byEmail(email).orElseThrow();
        if (!account.isMustChangePassword())
            throw new AuthWorkflowException(AuthWorkflowException.Kind.INVALID,
                    "Esta conta não precisa de actualizar a palavra-passe temporária.");
        account.changePassword(auth.encode(password));
        auth.record(account, "PASSWORD_CHANGED", "Account", account.getId(),
                "Palavra-passe temporária substituída no primeiro acesso");
    }

    private AuthSession session(Account account) {
        return new AuthSession(auth.token(account), account.getId(), account.getName(), account.getEmail(),
                account.getRole().name(), account.isMustChangePassword());
    }
}
