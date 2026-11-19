package com.agency.api.application.accounts;

import com.agency.api.domain.*;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AccountManagementUseCaseTest {
    private final AccountManagementPort port = mock(AccountManagementPort.class);
    private final AccountManagementUseCase useCase = new AccountManagementUseCase(port);
    private final Account agent = mock(Account.class);
    private final UUID agentId = UUID.randomUUID();

    AccountManagementUseCaseTest() {
        when(agent.getId()).thenReturn(agentId);
        when(agent.getRole()).thenReturn(Role.AGENT);
    }

    @Test
    void createsAgencyAccountWithHashedTemporaryCredentialAndAudit() {
        when(port.emailExists("client@example.test")).thenReturn(false);
        when(port.temporaryPassword()).thenReturn("Temp123!");
        when(port.encode("Temp123!")).thenReturn("encoded");
        when(port.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        ManagedAccount result = useCase.createAgencyAccount(agent, " Client ", "client@example.test",
                "CLIENTE", "123", "Address", new byte[]{1, 2}, "image/png");

        assertEquals("Temp123!", result.temporaryPassword());
        assertEquals(Role.CLIENT, result.account().getRole());
        assertTrue(result.account().isMustChangePassword());
        verify(port).saveClient(any(ClientProfile.class));
        verify(port).record(agent, "ACCOUNT_CREATED", "Account", null, "CLIENT client@example.test");
    }

    @Test
    void rejectsDuplicateEmailsInvalidRolesAndMissingClientPhoto() {
        when(port.emailExists("dup@example.test")).thenReturn(true);
        assertEquals(AccountManagementException.Kind.CONFLICT,
                assertThrows(AccountManagementException.class, () -> useCase.createAgencyAccount(agent,
                        "A Client", "dup@example.test", "CLIENT", "123", "Address", new byte[]{1}, "image/png"))
                        .kind());
        when(port.emailExists(anyString())).thenReturn(false);
        assertEquals(AccountManagementException.Kind.INVALID,
                assertThrows(AccountManagementException.class, () -> useCase.createAgencyAccount(agent,
                        "A Client", "x@example.test", "UNKNOWN", null, null, null, null)).kind());
        assertEquals(AccountManagementException.Kind.INVALID,
                assertThrows(AccountManagementException.class, () -> useCase.createAgencyAccount(agent,
                        "A Client", "x@example.test", "CLIENT", "123", "Address", null, null)).kind());
    }

    @Test
    void resetsTemporaryPasswordOnlyForAgencyAndExistingAccount() {
        UUID userId = UUID.randomUUID();
        Account account = mock(Account.class);
        when(port.account(userId)).thenReturn(Optional.of(account));
        when(port.temporaryPassword()).thenReturn("Reset123!");
        when(port.encode("Reset123!")).thenReturn("encoded");

        ManagedAccount result = useCase.resetTemporaryPassword(agent, userId);

        assertEquals("Reset123!", result.temporaryPassword());
        verify(account).setTemporaryPassword("encoded");
        verify(port).record(agent, "TEMPORARY_PASSWORD_ISSUED", "Account", userId,
                "Credencial temporária redefinida");
    }
}
