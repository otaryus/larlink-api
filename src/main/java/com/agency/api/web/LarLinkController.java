package com.agency.api.web;

import com.agency.api.domain.*;
import com.agency.api.application.matching.InvalidMatchingLocationException;
import com.agency.api.application.matching.SearchMatchesQuery;
import com.agency.api.application.matching.SearchMatchesUseCase;
import com.agency.api.service.ProfilePhotoService;
import com.agency.api.application.requests.CreateHireRequestCommand;
import com.agency.api.application.requests.CreateHireRequestUseCase;
import com.agency.api.application.requests.DecideHireRequestUseCase;
import com.agency.api.application.requests.DecideWorkerHireRequestUseCase;
import com.agency.api.application.requests.RequestConflictException;
import com.agency.api.application.requests.RequestForbiddenException;
import com.agency.api.application.requests.RequestNotFoundException;
import com.agency.api.application.requests.RequestValidationException;
import com.agency.api.application.contracts.ContractConflictException;
import com.agency.api.application.contracts.ContractForbiddenException;
import com.agency.api.application.contracts.ContractLifecycleUseCase;
import com.agency.api.application.contracts.ContractLifecycleNotFoundException;
import com.agency.api.application.contracts.ContractValidationException;
import com.agency.api.application.incidents.IncidentWorkflowUseCase;
import com.agency.api.application.incidents.IncidentNotFoundException;
import com.agency.api.application.incidents.IncidentForbiddenException;
import com.agency.api.application.incidents.IncidentValidationException;
import com.agency.api.application.profiles.ProfileWorkflowUseCase;
import com.agency.api.application.profiles.ProfileWorkflowException;
import com.agency.api.application.accounts.AccountManagementUseCase;
import com.agency.api.application.accounts.AccountManagementException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.time.*;
import java.util.*;

@RestController
@RequestMapping("/api")
public class LarLinkController {

    private final SearchMatchesUseCase searchMatches;
    private final ProfilePhotoService photos;
    private final CreateHireRequestUseCase createHireRequest;
    private final DecideHireRequestUseCase decideHireRequest;
    private final DecideWorkerHireRequestUseCase decideWorkerHireRequest;
    private final ContractLifecycleUseCase contractLifecycle;
    private final IncidentWorkflowUseCase incidentWorkflow;
    private final ProfileWorkflowUseCase profileWorkflow;
    private final AccountManagementUseCase accountManagement;

    public LarLinkController(SearchMatchesUseCase searchMatches, ProfilePhotoService photos,
            CreateHireRequestUseCase createHireRequest, DecideHireRequestUseCase decideHireRequest,
            DecideWorkerHireRequestUseCase decideWorkerHireRequest, ContractLifecycleUseCase contractLifecycle,
            IncidentWorkflowUseCase incidentWorkflow, ProfileWorkflowUseCase profileWorkflow,
            AccountManagementUseCase accountManagement) {
        this.searchMatches = searchMatches;
        this.photos = photos;
        this.createHireRequest = createHireRequest;
        this.decideHireRequest = decideHireRequest;
        this.decideWorkerHireRequest = decideWorkerHireRequest;
        this.contractLifecycle = contractLifecycle;
        this.incidentWorkflow = incidentWorkflow;
        this.profileWorkflow = profileWorkflow;
        this.accountManagement = accountManagement;
    }

    private Account me(Authentication auth) {
        return profileWorkflow.accountByEmail(auth.getName());
    }

    public record ClientInput(@NotBlank(message="Indique o telefone.") String phone, @Size(min = 5,message="Indique o endereço completo com pelo menos 5 caracteres.") String address) {}

    public record WorkerInput(@NotNull
            @Past LocalDate birthDate, @NotBlank String phone, @NotBlank String city, @NotBlank String district, @NotBlank String neighborhood, @NotEmpty Set<String> skills, @NotEmpty Set<String> availability, @Min(5000) int expectedSalary, @Min(0)
            @Max(50) int yearsOfExperience) {}

    public record SearchInput(String city, String district, String neighborhood, Set<String> skills, Set<String> availability, Integer maxSalary, String text) {}

    public record RequestInput(@NotNull UUID housekeeperId, Set<String> requiredSkills, Set<UUID> taskIds, @NotEmpty Set<String> requiredDays, @NotBlank String city, @NotBlank @Size(max=100) String district, @Size(max=100) String neighborhood, @NotBlank @Pattern(regexp="^([01]\\d|2[0-3]):[0-5]\\d$") String startTime, @NotBlank @Pattern(regexp="^([01]\\d|2[0-3]):[0-5]\\d$") String endTime, @NotNull
            @FutureOrPresent LocalDate startDate, @NotBlank
            @Size(max = 2000) String taskScope, @Min(5000) int monthlySalary, @Min(1)
            @Max(24) int durationMonths, @Min(1)
            @Max(12) int hoursPerDay, @Size(max = 300) String notes) {}

    public record AgencyUserInput(@NotBlank(message="Indique o nome completo.")
            @Size(min = 4,message="O nome deve ter pelo menos 4 caracteres.") String name, @Email(message="Indique um email válido.")
            @NotBlank(message="Indique o email.") String email, @NotBlank(message="Seleccione o tipo de conta.") String role, String phone, String address) {}

    public record IncidentInput(@NotNull UUID contractId, String type, @NotBlank
            @Size(min = 10, max = 2000) String description) {}

    @PostMapping("/clients/profile")
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<?> clientProfile(@Valid @RequestBody ClientInput in, Authentication auth) {
        try {
            return ResponseEntity.status(201).body(profileWorkflow.createClient(me(auth), in.phone(), in.address()));
        } catch (ProfileWorkflowException exception) {
            if (exception.kind() == ProfileWorkflowException.Kind.CONFLICT)
            return ResponseEntity.status(409).body(Map.of("error", "Perfil de cliente já existe."));
            throw exception;
        }
    }

    @GetMapping("/clients/me")
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<?> myClientProfile(Authentication auth) {
        return profileWorkflow.client(me(auth).getId()).<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/clients/me")
    @PreAuthorize("hasRole('CLIENT')")
    @Transactional
    public ResponseEntity<?> updateClientProfile(@Valid @RequestBody ClientInput in, Authentication auth) {
        try {
            return ResponseEntity.ok(profileWorkflow.updateClient(me(auth), in.phone(), in.address()));
        } catch (ProfileWorkflowException exception) {
            if (exception.kind() == ProfileWorkflowException.Kind.NOT_FOUND)
            return ResponseEntity.notFound().build();
            throw exception;
        }
    }

    @GetMapping("/accounts/{id}/photo")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> profilePhoto(@PathVariable UUID id, Authentication auth) {
        try {
            var photo = profileWorkflow.profilePhoto(id, me(auth).getId());
            return ResponseEntity.ok().contentType(MediaType.parseMediaType(photo.contentType()))
                    .cacheControl(CacheControl.noStore()).body(photo.bytes());
        } catch (ProfileWorkflowException exception) {
            return exception.kind() == ProfileWorkflowException.Kind.FORBIDDEN
                    ? ResponseEntity.status(403).build() : ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/housekeepers/profile")
    @PreAuthorize("hasAnyRole('HOUSEKEEPER','CANDIDATE')")
    public ResponseEntity<?> workerProfile(@Valid @RequestBody WorkerInput in, Authentication auth) {
        try {
            return ResponseEntity.status(201).body(profileWorkflow.createHousekeeper(me(auth), in.birthDate(),
                    in.phone(), in.city(), in.district(), in.neighborhood(), in.skills(), in.availability(),
                    in.expectedSalary(), in.yearsOfExperience()));
        } catch (ProfileWorkflowException exception) {
            if (exception.kind() == ProfileWorkflowException.Kind.CONFLICT)
                return ResponseEntity.status(409).body(Map.of("error", exception.getMessage()));
            return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
        }
    }

    @PutMapping("/housekeepers/me")
    @PreAuthorize("hasAnyRole('HOUSEKEEPER','CANDIDATE')")
    @Transactional
    public ResponseEntity<?> updateWorkerProfile(@Valid @RequestBody WorkerInput in, Authentication auth) {
        try {
            return ResponseEntity.ok(profileWorkflow.updateHousekeeper(me(auth), in.birthDate(), in.phone(), in.city(),
                    in.district(), in.neighborhood(), in.skills(), in.availability(), in.expectedSalary(),
                    in.yearsOfExperience()));
        } catch (ProfileWorkflowException exception) {
            if (exception.kind() == ProfileWorkflowException.Kind.NOT_FOUND)
                return ResponseEntity.notFound().build();
            if (exception.kind() == ProfileWorkflowException.Kind.CONFLICT)
                return ResponseEntity.status(409).body(Map.of("error", exception.getMessage()));
            return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
        }
    }

    @PostMapping("/matching")
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<?> matching(@Valid @RequestBody SearchInput in) {
        return match(in);
    }

    @GetMapping("/matching")
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<?> matchingQuery(@RequestParam(required = false) String city, @RequestParam(required = false) String district, @RequestParam(required = false) String neighborhood, @RequestParam(required = false) Set<String> skills, @RequestParam(required = false) Set<String> availability, @RequestParam(required = false) Integer maxSalary, @RequestParam(required = false) String text) {
        return match(new SearchInput(city, district, neighborhood, skills, availability, maxSalary, text));
    }

    private ResponseEntity<?> match(SearchInput input) {
        try {
            return ResponseEntity.ok(searchMatches.search(new SearchMatchesQuery(
                    input.city(), input.district(), input.neighborhood(), input.skills(),
                    input.availability(), input.maxSalary(), input.text())));
        } catch (InvalidMatchingLocationException exception) {
            return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
        }
    }

    @GetMapping("/housekeepers")
    @PreAuthorize("hasAnyRole('AGENT','SECRETARY')")
    public List<?> listHousekeepers() {
        var engaged = profileWorkflow.contractsForAgency().stream().filter(c -> "ACTIVE".equals(c.getStatus()))
                .map(c -> c.getHireRequest().getHousekeeper().getId())
                .collect(java.util.stream.Collectors.toSet());
        return profileWorkflow.housekeepers().stream().map(h -> Map.ofEntries(Map.entry("id", h.getId()),
                Map.entry("account", Map.of("id", h.getAccount().getId(), "name", h.getAccount().getName())),
                Map.entry("name", h.getName()), Map.entry("phone", h.getPhone()), Map.entry("city", h.getCity()),
                Map.entry("district", h.getDistrict()), Map.entry("neighborhood", h.getNeighborhood()),
                Map.entry("skills", h.getSkills()), Map.entry("availability", h.getAvailability()),
                Map.entry("expectedSalary", h.getExpectedSalary()),
                Map.entry("yearsOfExperience", h.getYearsOfExperience()), Map.entry("status", h.getStatus()),
                Map.entry("availabilityStatus", engaged.contains(h.getId()) ? "CONTRACTED"
                        : h.getStatus().equals("APPROVED") ? "AVAILABLE" : "NOT_APPROVED"),
                Map.entry("rejectionReason", h.getRejectionReason() == null ? "" : h.getRejectionReason())))
                .toList();
    }

    @GetMapping("/housekeepers/me")
    @PreAuthorize("hasAnyRole('HOUSEKEEPER','CANDIDATE')")
    public ResponseEntity<?> myWorkerProfile(Authentication auth) {
        return profileWorkflow.housekeeperProfile(me(auth).getId()).<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping(value = "/housekeepers/me/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('HOUSEKEEPER','CANDIDATE')")
    @Transactional
    public ResponseEntity<?> updateMyWorkerPhoto(@RequestPart("photo") MultipartFile photo, Authentication auth) {
        var account = me(auth);
        try {
            var image = photos.readValidated(photo);
            profileWorkflow.updatePhoto(account, image.bytes(), image.contentType());
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (ProfileWorkflowException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/requests")
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<?> createRequest(@Valid @RequestBody RequestInput in, Authentication auth) {
        try {
            var request = createHireRequest.create(me(auth).getId(), new CreateHireRequestCommand(
                    in.housekeeperId(), in.requiredSkills(), in.taskIds(), in.requiredDays(), in.city(),
                    in.district(), in.neighborhood(), in.startTime(), in.endTime(), in.startDate(),
                    in.taskScope(), in.monthlySalary(), in.durationMonths(), in.hoursPerDay(), in.notes()));
            return ResponseEntity.status(201).body(request);
        } catch (RequestConflictException exception) {
            return ResponseEntity.status(409).body(Map.of("error", exception.getMessage()));
        } catch (RequestValidationException exception) {
            return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
        }
    }

    @GetMapping("/requests")
    @PreAuthorize("hasAnyRole('CLIENT','AGENT','SECRETARY')")
    public List<?> getRequests(Authentication auth) {
        return profileWorkflow.requests(me(auth));
    }

    @GetMapping("/requests/inbox")
    @PreAuthorize("hasAnyRole('HOUSEKEEPER','CANDIDATE')")
    public List<?> workerInbox(Authentication auth) {
        return profileWorkflow.workerInbox(me(auth).getId());
    }

    @PatchMapping("/requests/{id}/decision")
    @PreAuthorize("hasRole('AGENT')")
    public ResponseEntity<?> decision(@PathVariable UUID id, @RequestParam String status, @RequestParam(required = false) String reason, Authentication auth) {
        try {
            return ResponseEntity.ok(decideHireRequest.decide(id, me(auth).getId(), status, reason));
        } catch (RequestNotFoundException exception) {
            return ResponseEntity.notFound().build();
        } catch (RequestValidationException exception) {
            return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
        }
    }

    @PatchMapping("/requests/{id}/worker-decision")
    @PreAuthorize("hasAnyRole('HOUSEKEEPER','CANDIDATE')")
    public ResponseEntity<?> workerDecision(@PathVariable UUID id, @RequestParam boolean accepted, Authentication auth) {
        try {
            var result = decideWorkerHireRequest.decide(id, me(auth).getId(), accepted);
            if (result.contract() == null) return ResponseEntity.ok(Map.of("request", result.request()));
            return ResponseEntity.ok(Map.of("request", result.request(), "contract", result.contract()));
        } catch (RequestNotFoundException exception) {
            return ResponseEntity.notFound().build();
        } catch (RequestForbiddenException exception) {
            return ResponseEntity.status(403).body(Map.of("error", exception.getMessage()));
        } catch (RequestConflictException exception) {
            return ResponseEntity.status(409).body(Map.of("error", exception.getMessage()));
        } catch (RequestValidationException exception) {
            return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
        }
    }

    @GetMapping("/contracts")
    @PreAuthorize("hasAnyRole('CLIENT','HOUSEKEEPER','CANDIDATE','AGENT')")
    public List<?> getContracts(Authentication auth) {
        return profileWorkflow.contracts(me(auth));
    }

    @GetMapping("/time")
    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> serverTime() {
        return profileWorkflow.serverTime();
    }

    public record ContractSignatureInput(@NotBlank @Size(max=150) String fullName, boolean contractRead) {}

    @PatchMapping("/contracts/{id}/sign")
    @PreAuthorize("hasAnyRole('CLIENT','HOUSEKEEPER','CANDIDATE')")
    public ResponseEntity<?> signContract(@PathVariable UUID id, @Valid @RequestBody ContractSignatureInput input, Authentication auth) {
        try {
            return ResponseEntity.ok(contractLifecycle.sign(id, me(auth).getId(),
                    input.fullName(), input.contractRead()));
        } catch (ContractLifecycleNotFoundException exception) {
            return ResponseEntity.notFound().build();
        } catch (ContractForbiddenException exception) {
            return ResponseEntity.status(403).body(Map.of("error", exception.getMessage()));
        } catch (ContractConflictException exception) {
            return ResponseEntity.status(409).body(Map.of("error", exception.getMessage()));
        } catch (ContractValidationException exception) {
            return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
        }
    }

    @PatchMapping("/agent/contracts/{id}/terminate")
    @PreAuthorize("hasRole('AGENT')")
    public ResponseEntity<?> terminateContract(@PathVariable UUID id, @RequestParam @NotBlank @Size(min = 10, max = 1000) String reason, Authentication auth) {
        try {
            return ResponseEntity.ok(contractLifecycle.terminate(id, me(auth).getId(), reason));
        } catch (ContractLifecycleNotFoundException exception) {
            return ResponseEntity.notFound().build();
        } catch (ContractValidationException exception) {
            return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
        }
    }

    @PatchMapping("/contracts/{id}/started")
    @PreAuthorize("hasAnyRole('CLIENT','HOUSEKEEPER','CANDIDATE')")
    public ResponseEntity<?> confirmWorkStarted(@PathVariable UUID id, Authentication auth) {
        try {
            return ResponseEntity.ok(contractLifecycle.confirmStarted(id, me(auth).getId()));
        } catch (ContractLifecycleNotFoundException exception) {
            return ResponseEntity.notFound().build();
        } catch (ContractForbiddenException exception) {
            return ResponseEntity.status(403).body(Map.of("error", exception.getMessage()));
        } catch (ContractValidationException exception) {
            return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
        }
    }

    @PatchMapping("/contracts/{id}/agency-review")
    @PreAuthorize("hasRole('AGENT')")
    public ResponseEntity<?> reviewContract(@PathVariable UUID id, Authentication auth) {
        try {
            return ResponseEntity.ok(contractLifecycle.review(id, me(auth).getId()));
        } catch (ContractLifecycleNotFoundException exception) {
            return ResponseEntity.notFound().build();
        }
    }

    public record AgencyReviewInput(boolean approved, @Size(min=10,max=1000) String note) {}
    @PatchMapping("/agent/contracts/{id}/review")
    @PreAuthorize("hasRole('AGENT')")
    public ResponseEntity<?> decideContractReview(@PathVariable UUID id, @Valid @RequestBody AgencyReviewInput input, Authentication auth) {
        try {
            var result = contractLifecycle.decideReview(id, me(auth).getId(), input.approved(), input.note());
            return ResponseEntity.ok(Map.of("id", id, "agencyReviewed", result.reviewed(),
                    "agencyReviewState", result.state(), "agencyReviewNote", result.note()));
        } catch (ContractLifecycleNotFoundException exception) {
            return ResponseEntity.notFound().build();
        } catch (ContractValidationException exception) {
            return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
        }
    }

    @PostMapping("/incidents")
    @PreAuthorize("hasAnyRole('CLIENT','HOUSEKEEPER','CANDIDATE')")
    @Transactional
    public ResponseEntity<?> reportIncident(@Valid @RequestBody IncidentInput in, Authentication auth) {
        try {
            return ResponseEntity.status(201).body(
                    incidentWorkflow.report(me(auth).getId(), in.contractId(), in.type(), in.description()));
        } catch (IncidentNotFoundException exception) {
            return ResponseEntity.badRequest().body(Map.of("error", "Contrato não encontrado."));
        } catch (IncidentForbiddenException exception) {
            return ResponseEntity.status(403).body(Map.of("error", exception.getMessage()));
        } catch (IncidentValidationException exception) {
            return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
        }
    }

    @GetMapping("/agent/incidents")
    @PreAuthorize("hasRole('AGENT')")
    public List<IncidentReport> incidentsForAgency() {
        return incidentWorkflow.listForAgency();
    }

    @PatchMapping("/agent/incidents/{id}/close")
    @PreAuthorize("hasRole('AGENT')")
    @Transactional
    public ResponseEntity<?> closeIncident(@PathVariable UUID id, Authentication auth) {
        try {
            return ResponseEntity.ok(incidentWorkflow.close(id, me(auth).getId()));
        } catch (IncidentNotFoundException exception) {
            return ResponseEntity.notFound().build();
        } catch (IncidentValidationException exception) {
            return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
        }
    }

    @PostMapping(value = "/agent/users", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('AGENT')")
    @Transactional
    public ResponseEntity<?> createAgencyUser(@Valid @RequestPart("account") AgencyUserInput in, @RequestPart(value = "photo", required = false) MultipartFile photo, Authentication auth) {
        ProfilePhotoService.Photo image = null;
        if (photo != null)try {
            image = photos.readValidated(photo);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
        try {
            var managed = accountManagement.createAgencyAccount(me(auth), in.name(), in.email(), in.role(),
                    in.phone(), in.address(), image == null ? null : image.bytes(),
                    image == null ? null : image.contentType());
            var account = managed.account();
            return ResponseEntity.status(201).body(Map.of("id", account.getId(), "name", account.getName(),
                    "email", account.getEmail(), "role", account.getRole(),
                    "temporaryPassword", managed.temporaryPassword(), "mustChangePassword", true));
        } catch (AccountManagementException exception) {
            return switch (exception.kind()) {
                case CONFLICT -> ResponseEntity.status(409).body(Map.of("error", exception.getMessage()));
                case INVALID -> ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
                case NOT_FOUND -> ResponseEntity.notFound().build();
                case FORBIDDEN -> ResponseEntity.status(403).build();
            };
        }
    }

    @PostMapping("/agent/users/{id}/temporary-password")
    @PreAuthorize("hasRole('AGENT')")
    @Transactional
    public ResponseEntity<?> resetTemporaryPassword(@PathVariable UUID id, Authentication auth) {
        try {
            var managed = accountManagement.resetTemporaryPassword(me(auth), id);
            var account = managed.account();
            return ResponseEntity.ok(Map.of("id", account.getId(), "name", account.getName(), "email",
                    account.getEmail(), "temporaryPassword", managed.temporaryPassword(),
                    "mustChangePassword", true));
        } catch (AccountManagementException exception) {
            if (exception.kind() == AccountManagementException.Kind.NOT_FOUND)
                return ResponseEntity.notFound().build();
            return ResponseEntity.status(403).build();
        }
    }

    @GetMapping("/agent/dashboard")
    @PreAuthorize("hasRole('AGENT')")
    public Map<String, Object> dashboard() {
        return profileWorkflow.dashboard();
    }

    @PatchMapping("/housekeepers/{id}/decision")
    @PreAuthorize("hasRole('AGENT')")
    @Transactional
    public ResponseEntity<?> decideProfile(@PathVariable UUID id, @RequestParam String status, @RequestParam(required = false) String reason, Authentication auth) {
        try {
            return ResponseEntity.ok(profileWorkflow.decideHousekeeper(me(auth), id, status, reason));
        } catch (ProfileWorkflowException exception) {
            if (exception.kind() == ProfileWorkflowException.Kind.NOT_FOUND)
                return ResponseEntity.notFound().build();
            return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
        }
    }
}
