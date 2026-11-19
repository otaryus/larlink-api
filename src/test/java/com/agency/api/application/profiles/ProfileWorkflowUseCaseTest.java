package com.agency.api.application.profiles;

import com.agency.api.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProfileWorkflowUseCaseTest {
    private final UUID accountId = UUID.randomUUID();
    private final ProfileWorkflowPort port = mock(ProfileWorkflowPort.class);
    private final ProfileWorkflowUseCase useCase = new ProfileWorkflowUseCase(port);
    private final Account account = mock(Account.class);

    @BeforeEach
    void setup() {
        when(account.getId()).thenReturn(accountId);
        when(account.getRole()).thenReturn(Role.CLIENT);
        when(port.today()).thenReturn(LocalDate.of(2026, 11, 19));
        when(port.validLocation("Maputo", "KaMpfumu")).thenReturn(true);
        when(port.saveClient(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(port.saveHousekeeper(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createsAndUpdatesClientProfileWithOwnershipAndAudit() {
        when(port.clientProfile(accountId)).thenReturn(Optional.empty());
        ClientProfile client = useCase.createClient(account, "123", "Maputo");
        assertEquals("123", client.getPhone());
        when(port.clientProfile(accountId)).thenReturn(Optional.of(client));
        useCase.updateClient(account, " 456 ", " Address ");
        assertEquals("456", client.getPhone());
        verify(port).record(account, "CLIENT_PROFILE_UPDATED", "ClientProfile", client.getId(),
                "Contactos e endereço actualizados");
    }

    @Test
    void validatesWorkerAgeLocationAndUniquePhone() {
        when(port.housekeeperProfile(accountId)).thenReturn(Optional.empty());
        when(port.phoneExists("123")).thenReturn(false);
        assertThrows(ProfileWorkflowException.class, () -> useCase.createHousekeeper(account,
                LocalDate.of(2010, 1, 1), "123", "Maputo", "KaMpfumu", "Centro",
                Set.of("CLEANING"), Set.of("MON"), 6000, 1));
        assertThrows(ProfileWorkflowException.class, () -> useCase.createHousekeeper(account,
                LocalDate.of(1990, 1, 1), "123", "Other", "Invalid", "Centro",
                Set.of("CLEANING"), Set.of("MON"), 6000, 1));
        when(port.phoneExists("123")).thenReturn(true);
        assertThrows(ProfileWorkflowException.class, () -> useCase.createHousekeeper(account,
                LocalDate.of(1990, 1, 1), "123", "Maputo", "KaMpfumu", "Centro",
                Set.of("CLEANING"), Set.of("MON"), 6000, 1));
    }

    @Test
    void decidesPendingWorkerAndSendsAuditAndNotification() {
        HousekeeperProfile profile = mock(HousekeeperProfile.class);
        Account worker = mock(Account.class);
        UUID profileId = UUID.randomUUID();
        UUID workerId = UUID.randomUUID();
        when(profile.getId()).thenReturn(profileId);
        when(profile.getAccount()).thenReturn(worker);
        when(worker.getId()).thenReturn(workerId);
        when(profile.getStatus()).thenReturn("PENDING");
        when(port.housekeeper(profileId)).thenReturn(Optional.of(profile));

        assertSame(profile, useCase.decideHousekeeper(account, profileId, "REJECTED",
                "Reason is sufficiently long"));

        verify(profile).decide("REJECTED", "Reason is sufficiently long");
        verify(port).record(account, "CANDIDATE_PROFILE_REJECTED", "HousekeeperProfile",
                profileId, "Reason is sufficiently long");
        verify(port).notify(workerId, "PROFILE_REJECTED",
                "O seu perfil precisa de alterações: Reason is sufficiently long", "HousekeeperProfile", profileId);
    }

    @Test
    void updatesPhotoOnManagedAccountAndPersistsIt() {
        HousekeeperProfile profile = mock(HousekeeperProfile.class);
        when(port.account(accountId)).thenReturn(Optional.of(account));
        when(port.housekeeperProfile(accountId)).thenReturn(Optional.of(profile));

        assertSame(profile, useCase.updatePhoto(account, new byte[] {1, 2}, "image/png"));

        verify(account).setProfilePhoto(new byte[] {1, 2}, "image/png");
        verify(port).saveAccount(account);
        verify(port).record(account, "PROFILE_PHOTO_UPDATED", "Account", accountId,
                "Fotografia de perfil actualizada");
    }
}
