package com.agency.api.application.accounts;

import com.agency.api.domain.*;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuthWorkflowUseCaseTest {
    private final AuthPort port = mock(AuthPort.class);
    private final AuthWorkflowUseCase useCase = new AuthWorkflowUseCase(port);

    @Test
    void registersClientAndNotifiesAgency() {
        when(port.byEmail("user@test.local")).thenReturn(Optional.empty());
        when(port.encode("secret")).thenReturn("hashed");
        when(port.save(any())).thenAnswer(invocation -> {
            Account account = invocation.getArgument(0);
            return account;
        });
        when(port.token(any())).thenReturn("token");

        AuthSession session = useCase.register(" User ", "user@test.local", "CLIENT", "secret",
                new byte[]{1, 2}, "image/png");

        assertEquals("token", session.token());
        assertEquals("User", session.name());
        assertEquals("CLIENT", session.role());
        verify(port).notifyRole(Role.AGENT, "NEW_CLIENT", "Novo cliente registado.", "Account", null);
    }

    @Test
    void rejectsAgencyRoleAndDuplicateEmail() {
        when(port.byEmail(anyString())).thenReturn(Optional.empty());
        assertEquals(AuthWorkflowException.Kind.FORBIDDEN,
                assertThrows(AuthWorkflowException.class, () -> useCase.register("Name", "a@b.c",
                        "AGENCY", "pw", new byte[]{1}, "image/png")).kind());
        when(port.byEmail("taken@test.local")).thenReturn(Optional.of(mock(Account.class)));
        assertEquals(AuthWorkflowException.Kind.CONFLICT,
                assertThrows(AuthWorkflowException.class, () -> useCase.register("Name", "taken@test.local",
                        "CLIENT", "pw", new byte[]{1}, "image/png")).kind());
    }

    @Test
    void loginUsesSameGenericInvalidCredentialsResponse() {
        when(port.byEmail("missing@test.local")).thenReturn(Optional.empty());
        AuthWorkflowException exception = assertThrows(AuthWorkflowException.class,
                () -> useCase.login("missing@test.local", "wrong"));
        assertEquals(AuthWorkflowException.Kind.UNAUTHORIZED, exception.kind());
        assertEquals("Email ou palavra-passe inválidos.", exception.getMessage());
    }

    @Test
    void changesOnlyTemporaryPasswordAfterConfirmation() {
        Account account = mock(Account.class);
        when(account.isMustChangePassword()).thenReturn(true);
        when(account.getId()).thenReturn(UUID.randomUUID());
        when(port.byEmail("user@test.local")).thenReturn(Optional.of(account));
        when(port.encode("next")).thenReturn("encoded");

        useCase.changeTemporaryPassword("user@test.local", "next", "next");

        verify(account).changePassword("encoded");
        assertEquals(AuthWorkflowException.Kind.INVALID,
                assertThrows(AuthWorkflowException.class, () -> useCase.changeTemporaryPassword(
                        "user@test.local", "next", "different")).kind());
    }
}
