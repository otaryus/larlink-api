package com.agency.api.application.management;

import com.agency.api.domain.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ManagementUseCaseTest {
    private final ManagementPort port = mock(ManagementPort.class);
    private final ManagementUseCase useCase = new ManagementUseCase(port);
    private final Account agent = mock(Account.class);
    private final UUID agentId = UUID.randomUUID();

    ManagementUseCaseTest() {
        when(agent.getId()).thenReturn(agentId);
        when(agent.getRole()).thenReturn(Role.AGENT);
    }

    @Test
    void createsAndUpdatesTasksWithAuditsAndValidation() {
        when(port.taskExistsByName("Cleaning")).thenReturn(false);
        when(port.saveTask(any())).thenAnswer(invocation -> invocation.getArgument(0));
        AgencyTask task = useCase.createTask(agent, "Cleaning", "Weekly", "CLEANING");
        assertEquals("Cleaning", task.getName());
        verify(port).record(agent, "TASK_CREATED", "AgencyTask", null, "Cleaning");

        UUID taskId = UUID.randomUUID();
        when(port.task(taskId)).thenReturn(Optional.of(task));
        assertSame(task, useCase.updateTask(agent, taskId, "Deep clean", null, false, "COOKING"));
        assertEquals("Deep clean", task.getName());
        assertFalse(task.isActive());
        assertThrows(ManagementValidationException.class,
                () -> useCase.createTask(agent, "Other", null, "INVALID"));
        when(port.taskExistsByName("Duplicate")).thenReturn(true);
        assertThrows(ManagementConflictException.class,
                () -> useCase.createTask(agent, "Duplicate", null, "OTHER"));
    }

    @Test
    void financeSettingsAndTestDateAreUpdatedThroughPort() {
        AgencyFinancePolicy[] stored = new AgencyFinancePolicy[1];
        when(port.financePolicy()).thenAnswer(invocation -> Optional.ofNullable(stored[0]));
        when(port.saveFinancePolicy(any())).thenAnswer(invocation -> {
            stored[0] = invocation.getArgument(0);
            return stored[0];
        });
        when(port.testDateEnabled()).thenReturn(true);
        when(port.serverDate()).thenReturn(java.time.LocalDate.of(2026, 11, 19));

        AgencyFinancePolicy policy = useCase.updateFinancePolicy(agent, 26, BigDecimal.TEN,
                BigDecimal.ONE, 12, 30, BigDecimal.valueOf(8), "settings");
        assertEquals(26, policy.getMonthlyDivisor());
        assertEquals(BigDecimal.valueOf(8), policy.getAgencyFeePercent());

        var date = useCase.updateTestDate(agent, java.time.LocalDate.of(2026, 11, 20));
        assertEquals(java.time.LocalDate.of(2026, 11, 19), date.get("serverDate"));
        assertEquals("2026-11-20", date.get("testDateOverride"));
    }

    @Test
    void hidesTestDateFeaturesWhenDisabled() {
        when(port.testDateEnabled()).thenReturn(false);
        assertThrows(ManagementNotFoundException.class, useCase::testDate);
        assertThrows(ManagementNotFoundException.class, () -> useCase.updateTestDate(agent, null));
    }
}
