package com.agency.api.application.profiles;

import com.agency.api.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.Period;
import java.util.*;

@Service
public class ProfileWorkflowUseCase {
    private static final String LOCATION_ERROR =
            "Seleccione um distrito pertencente à província ou cidade escolhida.";
    private final ProfileWorkflowPort profiles;

    public ProfileWorkflowUseCase(ProfileWorkflowPort profiles) { this.profiles = profiles; }

    @Transactional(readOnly = true)
    public Account accountByEmail(String email) {
        return profiles.accountByEmail(email).orElseThrow();
    }

    @Transactional
    public ClientProfile createClient(Account actor, String phone, String address) {
        if (profiles.clientProfile(actor.getId()).isPresent())
            throw new ProfileWorkflowException(ProfileWorkflowException.Kind.CONFLICT,
                    "Perfil de cliente já existe.");
        return profiles.saveClient(new ClientProfile(actor, phone, address));
    }

    @Transactional(readOnly = true)
    public Optional<ClientProfile> client(UUID accountId) { return profiles.clientProfile(accountId); }

    @Transactional
    public ClientProfile updateClient(Account actor, String phone, String address) {
        ClientProfile client = profiles.clientProfile(actor.getId()).orElseThrow(() ->
                new ProfileWorkflowException(ProfileWorkflowException.Kind.NOT_FOUND, ""));
        client.update(phone.trim(), address.trim());
        profiles.record(actor, "CLIENT_PROFILE_UPDATED", "ClientProfile", client.getId(),
                "Contactos e endereço actualizados");
        return client;
    }

    @Transactional(readOnly = true)
    public ProfilePhoto profilePhoto(UUID targetId, UUID viewerId) {
        Account target = profiles.account(targetId).orElseThrow(() ->
                new ProfileWorkflowException(ProfileWorkflowException.Kind.NOT_FOUND, ""));
        if (target.getProfilePhoto() == null)
            throw new ProfileWorkflowException(ProfileWorkflowException.Kind.NOT_FOUND, "");
        Account viewer = profiles.account(viewerId).orElseThrow();
        boolean allowed = viewer.getId().equals(target.getId()) || viewer.getRole() == Role.AGENT
                || viewer.getRole() == Role.SECRETARY;
        if (!allowed && viewer.getRole() == Role.CLIENT) {
            allowed = profiles.housekeeperProfile(targetId).map(h -> "APPROVED".equals(h.getStatus())).orElse(false);
        }
        if (!allowed) throw new ProfileWorkflowException(ProfileWorkflowException.Kind.FORBIDDEN, "");
        return new ProfilePhoto(target.getProfilePhoto(), target.getProfilePhotoContentType());
    }

    @Transactional
    public HousekeeperProfile createHousekeeper(Account actor, LocalDate birthDate, String phone, String city,
            String district, String neighborhood, Set<String> skills, Set<String> availability,
            int expectedSalary, int years) {
        validateLocation(city, district);
        if (profiles.housekeeperProfile(actor.getId()).isPresent())
            throw new ProfileWorkflowException(ProfileWorkflowException.Kind.CONFLICT, "Perfil já existe.");
        if (profiles.phoneExists(phone))
            throw new ProfileWorkflowException(ProfileWorkflowException.Kind.CONFLICT, "Telemóvel já registado.");
        validateAdult(birthDate);
        return profiles.saveHousekeeper(new HousekeeperProfile(actor, birthDate, phone, city, district,
                neighborhood, skills, availability, expectedSalary, years));
    }

    @Transactional
    public HousekeeperProfile updateHousekeeper(Account actor, LocalDate birthDate, String phone, String city,
            String district, String neighborhood, Set<String> skills, Set<String> availability,
            int expectedSalary, int years) {
        validateLocation(city, district);
        HousekeeperProfile profile = profiles.housekeeperProfile(actor.getId()).orElseThrow(() ->
                new ProfileWorkflowException(ProfileWorkflowException.Kind.NOT_FOUND, ""));
        if (profiles.phoneExistsForOther(phone, profile.getId()))
            throw new ProfileWorkflowException(ProfileWorkflowException.Kind.CONFLICT, "Telemóvel já registado.");
        validateAdult(birthDate);
        profile.updateProfile(birthDate, phone, city, district, neighborhood, skills, availability,
                expectedSalary, years);
        boolean employed = profiles.contracts().stream().anyMatch(contract ->
                contract.getHireRequest().getHousekeeper().getId().equals(profile.getId())
                        && "ACTIVE".equals(contract.getStatus()));
        if (employed) profile.decide("EMPLOYED", null);
        profiles.record(actor, "WORKER_PROFILE_UPDATED", "HousekeeperProfile", profile.getId(),
                employed ? "Perfil actualizado durante contrato activo"
                        : "Perfil actualizado e enviado para nova validação");
        return profile;
    }

    @Transactional(readOnly = true)
    public Optional<HousekeeperProfile> housekeeperProfile(UUID accountId) {
        return profiles.housekeeperProfile(accountId);
    }

    @Transactional
    public HousekeeperProfile updatePhoto(Account actor, byte[] bytes, String contentType) {
        Account managedActor = profiles.account(actor.getId()).orElseThrow(() ->
                new ProfileWorkflowException(ProfileWorkflowException.Kind.NOT_FOUND, ""));
        if (profiles.housekeeperProfile(managedActor.getId()).isEmpty())
            throw new ProfileWorkflowException(ProfileWorkflowException.Kind.NOT_FOUND, "");
        managedActor.setProfilePhoto(bytes, contentType);
        profiles.saveAccount(managedActor);
        profiles.record(managedActor, "PROFILE_PHOTO_UPDATED", "Account", managedActor.getId(),
                "Fotografia de perfil actualizada");
        return profiles.housekeeperProfile(managedActor.getId()).orElseThrow();
    }

    @Transactional(readOnly = true)
    public List<HousekeeperProfile> housekeepers() { return profiles.housekeepers(); }

    @Transactional(readOnly = true)
    public List<EmploymentContract> contractsForAgency() { return profiles.contractsForAgency(); }

    @Transactional(readOnly = true)
    public List<HireRequest> requests(Account actor) {
        return actor.getRole() == Role.CLIENT
                ? profiles.requestsForClient(actor.getId()) : profiles.requestsForAgency();
    }

    @Transactional(readOnly = true)
    public List<HireRequest> workerInbox(UUID accountId) { return profiles.requestsForWorker(accountId); }

    @Transactional(readOnly = true)
    public List<EmploymentContract> contracts(Account actor) {
        return switch (actor.getRole()) {
            case AGENT -> profiles.contractsForAgency();
            case CLIENT -> profiles.contractsForClient(actor.getId());
            case HOUSEKEEPER, CANDIDATE -> profiles.contractsForWorker(actor.getId());
            case SECRETARY -> List.of();
        };
    }

    @Transactional
    public HousekeeperProfile decideHousekeeper(Account actor, UUID profileId, String status, String reason) {
        HousekeeperProfile profile = profiles.housekeeper(profileId).orElseThrow(() ->
                new ProfileWorkflowException(ProfileWorkflowException.Kind.NOT_FOUND, ""));
        if (!"PENDING".equals(profile.getStatus()))
            throw new ProfileWorkflowException(ProfileWorkflowException.Kind.INVALID,
                    "Só perfis pendentes podem ser decididos.");
        if (!"APPROVED".equals(status) && !"REJECTED".equals(status))
            throw new ProfileWorkflowException(ProfileWorkflowException.Kind.INVALID, "Decisão inválida.");
        String normalizedReason = reason == null ? null : reason.trim();
        if ("REJECTED".equals(status) && (normalizedReason == null || normalizedReason.length() < 10))
            throw new ProfileWorkflowException(ProfileWorkflowException.Kind.INVALID,
                    "O motivo deve ter pelo menos 10 caracteres.");
        profile.decide(status, normalizedReason);
        profiles.record(actor, "CANDIDATE_PROFILE_" + status, "HousekeeperProfile", profileId,
                normalizedReason == null ? "Perfil aprovado" : normalizedReason);
        profiles.notify(profile.getAccount().getId(), "PROFILE_" + status,
                "APPROVED".equals(status) ? "O seu perfil foi aprovado pela agência."
                        : "O seu perfil precisa de alterações: " + normalizedReason,
                "HousekeeperProfile", profileId);
        return profile;
    }

    public Map<String,Object> serverTime() {
        return Map.of("serverDate", profiles.today(), "testDateEnabled", profiles.testDateEnabled());
    }

    @Transactional(readOnly = true)
    public Map<String,Object> dashboard() {
        List<HousekeeperProfile> workers = profiles.housekeepers();
        List<HireRequest> requests = profiles.requestsForAgency();
        List<EmploymentContract> contracts = profiles.contractsForAgency();
        long openIncidents = profiles.incidents().stream()
                .filter(i -> Set.of("ABERTA", "EM_ANALISE", "AGUARDA_RESPOSTA").contains(i.getStatus())).count();
        return Map.of("totalCandidates", workers.size(),
                "pendingProfiles", workers.stream().filter(h -> "PENDING".equals(h.getStatus())).count(),
                "approvedProfiles", workers.stream().filter(h -> "APPROVED".equals(h.getStatus())).count(),
                "pendingRequests", requests.stream().filter(r -> "PENDING".equals(r.getStatus())).count(),
                "totalClients", profiles.clientCount(), "totalContracts", contracts.size(),
                "openIncidents", openIncidents);
    }

    private void validateLocation(String city, String district) {
        if (!profiles.validLocation(city, district))
            throw new ProfileWorkflowException(ProfileWorkflowException.Kind.INVALID, LOCATION_ERROR);
    }

    private void validateAdult(LocalDate birthDate) {
        if (Period.between(birthDate, profiles.today()).getYears() < 18)
            throw new ProfileWorkflowException(ProfileWorkflowException.Kind.INVALID,
                    "É necessário ter pelo menos 18 anos.");
    }
}
