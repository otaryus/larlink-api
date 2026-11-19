package com.agency.api.application.requests;

import com.agency.api.application.matching.MatchDistancePolicy;
import com.agency.api.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeParseException;
import java.util.*;

@Service
public class CreateHireRequestUseCase {
    private static final Set<String> OPEN_STATUSES = Set.of("PENDING", "APPROVED", "EM_NEGOCIACAO");
    private final RequestCreationPort requests;
    private final MatchDistancePolicy distances = new MatchDistancePolicy();

    public CreateHireRequestUseCase(RequestCreationPort requests) {
        this.requests = requests;
    }

    @Transactional
    public HireRequest create(UUID clientId, CreateHireRequestCommand command) {
        if (command.monthlySalary() < 5_000) {
            throw new RequestValidationException("O salário proposto deve ser igual ou superior a 5.000 MT.");
        }
        if (command.monthlySalary() % 50 != 0) {
            throw new RequestValidationException("O salário proposto deve ser um múltiplo de 50 MT.");
        }
        validateSchedule(command);
        Account client = requests.findAccount(clientId).orElseThrow(RequestNotFoundException::new);
        if (!requests.hasClientProfile(clientId)) {
            throw new RequestValidationException("Complete primeiro o perfil de cliente.");
        }
        HousekeeperProfile worker = requests.findWorker(command.housekeeperId()).orElse(null);
        if (worker == null || !"APPROVED".equals(worker.getStatus())) {
            throw new RequestValidationException("Empregada indisponível.");
        }
        if (!requests.isValidLocation(command.city(), command.district())) {
            throw new RequestValidationException("Seleccione um distrito pertencente à província ou cidade escolhida.");
        }
        if (!requests.canonicalArea(command.city()).equalsIgnoreCase(worker.getProvince())) {
            throw new RequestValidationException("A profissional não pertence à província ou cidade seleccionada.");
        }
        Optional<RequestCreationPort.Coordinates> origin = requests.coordinates(command.city(), command.district());
        Optional<RequestCreationPort.Coordinates> candidate = requests.coordinates(worker.getProvince(), worker.getDistrict());
        if (origin.isPresent() && (candidate.isEmpty() || !distances.evaluate(
                origin.get().latitude(), origin.get().longitude(),
                candidate.get().latitude(), candidate.get().longitude()).eligible())) {
            throw new RequestValidationException("A profissional está fora do raio máximo de 25 km do local de trabalho.");
        }
        if (requests.isWorkerEmployed(worker.getId())) {
            throw new RequestValidationException("Esta candidata já está a trabalhar e não está disponível.");
        }
        Set<UUID> taskIds = command.taskIds() == null ? Set.of() : command.taskIds();
        Set<AgencyTask> selectedTasks = requests.activeTasks(taskIds);
        if (selectedTasks.size() != taskIds.size()) {
            throw new RequestValidationException("Uma tarefa seleccionada já não está disponível.");
        }
        Set<String> requiredSkills = new HashSet<>(command.requiredSkills() == null ? Set.of() : command.requiredSkills());
        selectedTasks.stream().map(AgencyTask::getSkillCode).forEach(requiredSkills::add);
        if (requiredSkills.isEmpty()) throw new RequestValidationException("Seleccione pelo menos uma tarefa.");
        Set<String> requiredDays = command.requiredDays() == null ? Set.of() : command.requiredDays();
        if (!worker.getSkills().containsAll(requiredSkills) || !worker.getAvailability().containsAll(requiredDays)) {
            throw new RequestValidationException("O perfil não corresponde aos requisitos.");
        }
        if (requests.hasOpenRequest(clientId, worker.getId())) {
            throw new RequestConflictException("Já existe um pedido pendente para este perfil.");
        }
        String completeScope = command.taskScope().trim();
        if (!selectedTasks.isEmpty()) {
            String taskNames = selectedTasks.stream().map(AgencyTask::getName).sorted()
                    .reduce((left, right) -> left + ", " + right).orElse("");
            completeScope += "\nTarefas seleccionadas no catálogo: " + taskNames;
        }
        HireRequest request = new HireRequest(client, worker, requiredSkills, requiredDays, command.startDate(),
                command.notes() == null ? "" : command.notes(), completeScope, command.monthlySalary(),
                command.durationMonths(), command.hoursPerDay());
        request.setWorkLocationAndSchedule(command.district().trim(),
                command.neighborhood() == null ? "" : command.neighborhood().trim(),
                command.startTime(), command.endTime());
        request.setSelectedTasks(selectedTasks);
        if (command.monthlySalary() < worker.getExpectedSalary()) {
            request.setStatus("EM_NEGOCIACAO");
        }
        request.setExpiration(Instant.now().plus(Duration.ofDays(requests.requestValidityDays())));
        requests.save(request);
        if (command.monthlySalary() < worker.getExpectedSalary()) {
            requests.saveInitialProposal(request, client, command.monthlySalary());
        }
        requests.record(client, "HIRE_REQUEST_CREATED", "HireRequest", request.getId(),
                "Oferta inicial: " + command.monthlySalary());
        requests.notify(worker.getAccount().getId(), "NEW_HIRE_REQUEST",
                "Recebeu um pedido directo. Reveja o salário e os termos.", "HireRequest", request.getId());
        return request;
    }

    private void validateSchedule(CreateHireRequestCommand command) {
        try {
            long minutes = Duration.between(LocalTime.parse(command.startTime()), LocalTime.parse(command.endTime())).toMinutes();
            if (minutes <= 0 || minutes % 60 != 0 || minutes / 60 != command.hoursPerDay()) {
                throw new RequestValidationException(
                        "O horário de saída deve ser posterior à entrada e corresponder a horas completas.");
            }
        } catch (DateTimeParseException exception) {
            throw new RequestValidationException("Indique um horário válido.");
        }
    }
}
