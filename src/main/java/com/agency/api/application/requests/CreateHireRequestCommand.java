package com.agency.api.application.requests;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

public record CreateHireRequestCommand(
        UUID housekeeperId,
        Set<String> requiredSkills,
        Set<UUID> taskIds,
        Set<String> requiredDays,
        String city,
        String district,
        String neighborhood,
        String startTime,
        String endTime,
        LocalDate startDate,
        String taskScope,
        int monthlySalary,
        int durationMonths,
        int hoursPerDay,
        String notes) {
}
