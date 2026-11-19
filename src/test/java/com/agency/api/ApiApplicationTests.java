package com.agency.api;

import com.agency.api.domain.Account;
import com.agency.api.domain.AbsenceRecord;
import com.agency.api.domain.Role;
import com.agency.api.domain.ClientProfile;
import com.agency.api.domain.AgencyTask;
import com.agency.api.domain.EmploymentContract;
import com.agency.api.domain.HireRequest;
import com.agency.api.domain.HousekeeperProfile;
import com.agency.api.domain.UserNotification;
import com.agency.api.repository.AbsenceRepository;
import com.agency.api.repository.AccountRepository;
import com.agency.api.repository.AdditionalWorkRepository;
import com.agency.api.repository.AgencyFinancePolicyRepository;
import com.agency.api.repository.AgencyTaskRepository;
import com.agency.api.repository.AuditEventRepository;
import com.agency.api.repository.ClientRepository;
import com.agency.api.repository.ContractRepository;
import com.agency.api.repository.HireRequestRepository;
import com.agency.api.repository.HousekeeperRepository;
import com.agency.api.repository.PaymentObligationRepository;
import com.agency.api.repository.UserNotificationRepository;
import com.agency.api.domain.PaymentObligation;
import com.agency.api.domain.AdditionalWork;
import com.agency.api.domain.AgencyFinancePolicy;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(properties = {"app.testing-date.enabled=true", "app.testing-payments.enabled=true"})
@AutoConfigureMockMvc
class ApiApplicationTests {
	@Autowired private MockMvc mvc;
	@Autowired private ObjectMapper mapper;
	@Autowired private AccountRepository accounts;
	@Autowired private ClientRepository clients;
	@Autowired private AbsenceRepository absences;
	@Autowired private AuditEventRepository auditEvents;
	@Autowired private AgencyTaskRepository tasks;
	@Autowired private UserNotificationRepository notifications;
	@Autowired private HousekeeperRepository housekeepers;
	@Autowired private HireRequestRepository requests;
	@Autowired private ContractRepository contracts;
	@Autowired private PaymentObligationRepository payments;
	@Autowired private AdditionalWorkRepository additionalWorks;
	@Autowired private AgencyFinancePolicyRepository financePolicies;
	@Autowired private PasswordEncoder encoder;

	@Test
	void contextLoads() {
	}

	@Test
	void agentOnlyDashboardIsProtectedByRole() throws Exception {
		String suffix = UUID.randomUUID().toString();
		String agentEmail = "agent-" + suffix + "@test.local";
		String clientEmail = "client-" + suffix + "@test.local";
		accounts.saveAndFlush(new Account("Agente Teste", agentEmail, encoder.encode("Agente@2026"), Role.AGENT));
		accounts.saveAndFlush(new Account("Cliente Teste", clientEmail, encoder.encode("Cliente@2026"), Role.CLIENT));

		mvc.perform(get("/api/agent/dashboard").header("Authorization", "Bearer " + login(agentEmail, "Agente@2026")))
				.andExpect(status().isOk());
		mvc.perform(get("/api/agent/dashboard").header("Authorization", "Bearer " + login(clientEmail, "Cliente@2026")))
				.andExpect(status().isForbidden());
	}

	@Test
	void temporaryPasswordMustBeChangedBeforeUsingNormalEndpoints() throws Exception {
		String email = "temp-" + UUID.randomUUID() + "@test.local";
		accounts.saveAndFlush(new Account("Secretária Teste", email, encoder.encode("Temporaria@2026"), Role.SECRETARY, true));
		String token = login(email, "Temporaria@2026");

		mvc.perform(get("/api/tasks").header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
		mvc.perform(post("/api/auth/change-password").header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(mapper.writeValueAsString(Map.of("password", "NovaSegura@2026", "confirmation", "NovaSegura@2026"))))
				.andExpect(status().isOk());
		mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content(mapper.writeValueAsString(Map.of("email", email, "password", "Temporaria@2026"))))
				.andExpect(status().isUnauthorized());
		String updatedToken = login(email, "NovaSegura@2026");
		mvc.perform(get("/api/tasks").header("Authorization", "Bearer " + updatedToken)).andExpect(status().isOk());
	}

	@Test
	void publicRegistrationRequiresProfilePhotoAndCannotCreateAgencyUsers() throws Exception {
		MockMultipartFile clientData = new MockMultipartFile("account", "", "application/json",
				mapper.writeValueAsBytes(Map.of("name", "Cliente Teste", "email", "photo-" + UUID.randomUUID() + "@test.local",
						"role", "CLIENT", "password", "Cliente@2026", "acceptTerms", true)));
		mvc.perform(multipart("/api/auth/register").file(clientData)).andExpect(status().isBadRequest());

		MockMultipartFile agentData = new MockMultipartFile("account", "", "application/json",
				mapper.writeValueAsBytes(Map.of("name", "Gestor Ilegal", "email", "agency-" + UUID.randomUUID() + "@test.local",
						"role", "AGENT", "password", "Gestor@2026", "acceptTerms", true)));
		mvc.perform(multipart("/api/auth/register").file(agentData)).andExpect(status().isForbidden());
	}

	@Test
	void profilePhotoAccessIsLimitedToTheOwnerAndAdministrativeRoles() throws Exception {
		String suffix = UUID.randomUUID().toString();
		Account owner = accounts.saveAndFlush(accountWithPhoto("Cliente Dono", "owner-" + suffix + "@test.local", Role.CLIENT));
		Account stranger = accounts.saveAndFlush(new Account("Cliente Terceiro", "stranger-" + suffix + "@test.local", encoder.encode("Cliente@2026"), Role.CLIENT));
		Account secretary = accounts.saveAndFlush(new Account("Secretária Teste", "secretary-" + suffix + "@test.local", encoder.encode("Secretaria@2026"), Role.SECRETARY));

		mvc.perform(get("/api/accounts/" + owner.getId() + "/photo")
				.header("Authorization", "Bearer " + login(owner.getEmail(), "Cliente@2026")))
				.andExpect(status().isOk()).andExpect(content().contentType("image/png"));
		mvc.perform(get("/api/accounts/" + owner.getId() + "/photo")
				.header("Authorization", "Bearer " + login(secretary.getEmail(), "Secretaria@2026")))
				.andExpect(status().isOk());
		mvc.perform(get("/api/accounts/" + owner.getId() + "/photo")
				.header("Authorization", "Bearer " + login(stranger.getEmail(), "Cliente@2026")))
				.andExpect(status().isForbidden());
	}

	@Test
	void candidateCanUploadPhotoAndMatchedClientCanViewIt() throws Exception {
		String suffix = UUID.randomUUID().toString();
		Account workerAccount = accounts.saveAndFlush(new Account("Candidata com Foto", "photo-worker-" + suffix + "@test.local", encoder.encode("Candidata@2026"), Role.CANDIDATE));
		HousekeeperProfile worker = new HousekeeperProfile(workerAccount, java.time.LocalDate.of(1990, 1, 1), "841234567", "Maputo", "KaMavota",
				"Costa do Sol", Set.of("CLEANING"), Set.of("MON"), 9000, 5);
		worker.decide("APPROVED", null);
		housekeepers.saveAndFlush(worker);
		Account client = accounts.saveAndFlush(new Account("Cliente com Foto", "photo-client-" + suffix + "@test.local", encoder.encode("Cliente@2026"), Role.CLIENT));
		String workerToken = login(workerAccount.getEmail(), "Candidata@2026");
		String clientToken = login(client.getEmail(), "Cliente@2026");
		byte[] image = new byte[]{(byte) 137, 80, 78, 71, 13, 10, 26, 10};
		MockMultipartFile photo = new MockMultipartFile("photo", "perfil.png", "image/png", image);

		mvc.perform(multipart(HttpMethod.PUT, "/api/housekeepers/me/photo").file(photo)
						.header("Authorization", "Bearer " + workerToken))
				.andExpect(status().isNoContent());
		mvc.perform(get("/api/accounts/" + workerAccount.getId() + "/photo")
						.header("Authorization", "Bearer " + clientToken))
				.andExpect(status().isOk())
				.andExpect(content().contentType("image/png"))
				.andExpect(content().bytes(image));
	}

	@Test
	void clientCanSendRequestToEligibleWorkerInNearbyDistrictWithoutNeighborhood() throws Exception {
		String suffix = UUID.randomUUID().toString();
		Account client = accounts.saveAndFlush(new Account("Cliente Distrito Próximo", "nearby-client-" + suffix + "@test.local", encoder.encode("Cliente@2026"), Role.CLIENT));
		clients.saveAndFlush(new ClientProfile(client, "841234567", "Rua de teste, Maputo"));
		Account workerAccount = accounts.saveAndFlush(new Account("Candidata Distrito Próximo", "nearby-worker-" + suffix + "@test.local", encoder.encode("Candidata@2026"), Role.CANDIDATE));
		HousekeeperProfile worker = new HousekeeperProfile(workerAccount, java.time.LocalDate.of(1990, 1, 1), "842345678", "Maputo", "KaMavota",
				"Costa do Sol", Set.of("CLEANING"), Set.of("MON"), 9000, 5);
		worker.decide("APPROVED", null);
		housekeepers.saveAndFlush(worker);
		String token = login(client.getEmail(), "Cliente@2026");
		Map<String, Object> request = Map.ofEntries(
				Map.entry("housekeeperId", worker.getId()),
				Map.entry("requiredSkills", Set.of("CLEANING")),
				Map.entry("requiredDays", Set.of("MON")),
				Map.entry("city", "Cidade de Maputo"),
				Map.entry("district", "KaMpfumu"),
				Map.entry("startTime", "08:00"),
				Map.entry("endTime", "16:00"),
				Map.entry("startDate", java.time.LocalDate.now(java.time.ZoneId.of("Africa/Maputo")).plusDays(1).toString()),
				Map.entry("taskScope", "Limpeza semanal de toda a casa"),
				Map.entry("monthlySalary", 9000),
				Map.entry("durationMonths", 12),
				Map.entry("hoursPerDay", 8),
				Map.entry("notes", ""));

		mvc.perform(post("/api/requests").header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(request)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.housekeeper.id").value(worker.getId().toString()));
	}

	@Test
	void agentCreatesSecretaryWithOneTimeTemporaryPasswordAndAuditTrail() throws Exception {
		String suffix = UUID.randomUUID().toString();
		String agentEmail = "manager-" + suffix + "@test.local";
		String secretaryEmail = "new-secretary-" + suffix + "@test.local";
		accounts.saveAndFlush(new Account("Gestor Teste", agentEmail, encoder.encode("Gestor@2026"), Role.AGENT));
		MockMultipartFile accountData = new MockMultipartFile("account", "", "application/json",
				mapper.writeValueAsBytes(Map.of("name", "Secretária Criada", "email", secretaryEmail, "role", "SECRETARY", "phone", "", "address", "")));

		MvcResult created = mvc.perform(multipart("/api/agent/users").file(accountData)
				.header("Authorization", "Bearer " + login(agentEmail, "Gestor@2026")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.temporaryPassword").isNotEmpty())
				.andExpect(jsonPath("$.mustChangePassword").value(true))
				.andReturn();

		String temporaryPassword = mapper.readTree(created.getResponse().getContentAsString()).path("temporaryPassword").asString();
		String token = login(secretaryEmail, temporaryPassword);
		mvc.perform(get("/api/tasks").header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
		org.junit.jupiter.api.Assertions.assertTrue(auditEvents.findTop500ByOrderByCreatedAtDesc().stream()
				.anyMatch(event -> event.getAction().equals("ACCOUNT_CREATED") && event.getDetails().contains(secretaryEmail)));
	}

	@Test
	void administrativeClientCreationRequiresAValidPhoto() throws Exception {
		String suffix = UUID.randomUUID().toString();
		String agentEmail = "manager-client-" + suffix + "@test.local";
		accounts.saveAndFlush(new Account("Gestor Teste", agentEmail, encoder.encode("Gestor@2026"), Role.AGENT));
		MockMultipartFile accountData = new MockMultipartFile("account", "", "application/json",
				mapper.writeValueAsBytes(Map.of("name", "Cliente Criado", "email", "created-client-" + suffix + "@test.local", "role", "CLIENT", "phone", "841234567", "address", "Polana, Maputo")));

		mvc.perform(multipart("/api/agent/users").file(accountData)
				.header("Authorization", "Bearer " + login(agentEmail, "Gestor@2026")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("Carregue uma fotografia de perfil."));
	}

	@Test
	void clientsOnlySeeActiveTasksAndCannotManageTheCatalog() throws Exception {
		String suffix = UUID.randomUUID().toString();
		String clientEmail = "task-client-" + suffix + "@test.local";
		accounts.saveAndFlush(new Account("Cliente Teste", clientEmail, encoder.encode("Cliente@2026"), Role.CLIENT));
		var active = tasks.saveAndFlush(new AgencyTask("Tarefa activa " + suffix, "Descrição", "CLEANING"));
		var inactive = new AgencyTask("Tarefa inactiva " + suffix, "Descrição", "LAUNDRY");
		inactive.update(inactive.getName(), inactive.getDescription(), false, inactive.getSkillCode());
		tasks.saveAndFlush(inactive);

		mvc.perform(get("/api/tasks").header("Authorization", "Bearer " + login(clientEmail, "Cliente@2026")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.id == '" + active.getId() + "')]").exists())
				.andExpect(jsonPath("$[?(@.id == '" + inactive.getId() + "')]").doesNotExist());
		mvc.perform(post("/api/agent/tasks").header("Authorization", "Bearer " + login(clientEmail, "Cliente@2026"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(mapper.writeValueAsString(Map.of("name", "Proibida", "description", "", "skillCode", "OTHER", "active", true))))
				.andExpect(status().isForbidden());
	}

	@Test
	void notificationsAndAdministrativeReportsRespectOwnershipAndRole() throws Exception {
		String suffix = UUID.randomUUID().toString();
		Account owner = accounts.saveAndFlush(new Account("Cliente Notificado", "notice-owner-" + suffix + "@test.local", encoder.encode("Cliente@2026"), Role.CLIENT));
		Account stranger = accounts.saveAndFlush(new Account("Cliente Estranho", "notice-stranger-" + suffix + "@test.local", encoder.encode("Cliente@2026"), Role.CLIENT));
		Account agent = accounts.saveAndFlush(new Account("Gestor Relatórios", "reports-agent-" + suffix + "@test.local", encoder.encode("Gestor@2026"), Role.AGENT));
		UserNotification notification = notifications.saveAndFlush(new UserNotification(owner, "TEST_EVENT", "Mensagem privada", "Account", owner.getId()));

		mvc.perform(get("/api/notifications").header("Authorization", "Bearer " + login(owner.getEmail(), "Cliente@2026")))
				.andExpect(status().isOk()).andExpect(jsonPath("$[0].message").value("Mensagem privada"));
		mvc.perform(patch("/api/notifications/" + notification.getId() + "/read")
				.header("Authorization", "Bearer " + login(stranger.getEmail(), "Cliente@2026")))
				.andExpect(status().isForbidden());
		mvc.perform(get("/api/agent/reports").header("Authorization", "Bearer " + login(stranger.getEmail(), "Cliente@2026")))
				.andExpect(status().isForbidden());
		mvc.perform(get("/api/agent/reports").header("Authorization", "Bearer " + login(agent.getEmail(), "Gestor@2026")))
				.andExpect(status().isOk()).andExpect(jsonPath("$.contracts").isNumber());
		mvc.perform(get("/api/agent/audit").header("Authorization", "Bearer " + login(agent.getEmail(), "Gestor@2026")))
				.andExpect(status().isOk());
	}

	@Test
	void initialSalaryOfferMustUseFiftyMeticalIncrements() throws Exception {
		String email = "salary-" + UUID.randomUUID() + "@test.local";
		accounts.saveAndFlush(new Account("Cliente Teste", email, encoder.encode("Cliente@2026"), Role.CLIENT));
		String token = login(email, "Cliente@2026");
		Map<String, Object> request = Map.ofEntries(
				Map.entry("housekeeperId", UUID.randomUUID()),
				Map.entry("requiredDays", java.util.Set.of("MONDAY")),
				Map.entry("city", "Maputo Cidade"),
				Map.entry("district", "KaMpfumo"),
				Map.entry("neighborhood", "Polana"),
				Map.entry("startTime", "08:00"),
				Map.entry("endTime", "16:00"),
				Map.entry("startDate", java.time.LocalDate.now().plusDays(1).toString()),
				Map.entry("taskScope", "Limpeza semanal da casa"),
				Map.entry("monthlySalary", 5251),
				Map.entry("durationMonths", 12),
				Map.entry("hoursPerDay", 8),
				Map.entry("notes", ""));

		mvc.perform(post("/api/requests").header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(request)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("O salário proposto deve ser um múltiplo de 50 MT."));
	}

	@Test
	void candidateMaySubmitProposalForAnExistingRequest() throws Exception {
		String email = "candidate-" + UUID.randomUUID() + "@test.local";
		accounts.saveAndFlush(new Account("Candidata Teste", email, encoder.encode("Candidata@2026"), Role.CANDIDATE));
		String token = login(email, "Candidata@2026");

		mvc.perform(post("/api/requests/" + UUID.randomUUID() + "/proposals")
						.header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content(mapper.writeValueAsString(Map.of("monthlySalary", 5000, "message", "Proposta teste"))))
				.andExpect(status().isNotFound());
	}

	@Test
	void workerSalaryProposalCanBeAcceptedByEmployerToGenerateContract() throws Exception {
		String suffix = UUID.randomUUID().toString();
		Account client = accounts.saveAndFlush(new Account("Cliente Proposta", "client-proposal-" + suffix + "@test.local", encoder.encode("Cliente@2026"), Role.CLIENT));
		Account workerAccount = accounts.saveAndFlush(new Account("Trabalhadora Proposta", "worker-proposal-" + suffix + "@test.local", encoder.encode("Trabalhadora@2026"), Role.HOUSEKEEPER));
		HousekeeperProfile worker = housekeepers.saveAndFlush(new HousekeeperProfile(workerAccount,
				java.time.LocalDate.of(1990, 1, 1), "85" + suffix.substring(0, 7), "Maputo", "KaMpfumu",
				"Polana", Set.of("CLEANING"), Set.of("MON"), 9000, 5));
		HireRequest request = requests.saveAndFlush(new HireRequest(client, worker, Set.of("CLEANING"),
				Set.of("MON"), java.time.LocalDate.now().plusDays(1), "", "Limpeza semanal", 8000, 6, 8));
		request.setStatus("EM_NEGOCIACAO");
		String workerToken = login(workerAccount.getEmail(), "Trabalhadora@2026");
		String clientToken = login(client.getEmail(), "Cliente@2026");

		MvcResult submitted = mvc.perform(post("/api/requests/" + request.getId() + "/proposals")
						.header("Authorization", "Bearer " + workerToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(mapper.writeValueAsString(Map.of("monthlySalary", 9000, "message", "Salário pretendido"))))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.authorName").value("Trabalhadora Proposta"))
				.andReturn();
		String proposalId = mapper.readTree(submitted.getResponse().getContentAsString()).path("id").asString();
		mvc.perform(get("/api/requests/" + request.getId() + "/proposals")
						.header("Authorization", "Bearer " + clientToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].authorName").value("Trabalhadora Proposta"))
				.andExpect(jsonPath("$[0].monthlySalary").value(9000));
		mvc.perform(patch("/api/requests/" + request.getId() + "/proposals/" + proposalId)
						.header("Authorization", "Bearer " + clientToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(mapper.writeValueAsString(Map.of("decision", "ACCEPTED"))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.requestStatus").value("CONTRATO_GERADO"));
		mvc.perform(get("/api/contracts").header("Authorization", "Bearer " + clientToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].status").value("AWAITING_SIGNATURES"));
	}

	@Test
	void contractSignaturesRequireReadingAndTheExactRegisteredNameForBothParties() throws Exception {
		String suffix = UUID.randomUUID().toString();
		Account client = accounts.saveAndFlush(new Account("Cliente Exacto", "client-sign-" + suffix + "@test.local", encoder.encode("Cliente@2026"), Role.CLIENT));
		Account workerAccount = accounts.saveAndFlush(new Account("Amélia João", "worker-sign-" + suffix + "@test.local", encoder.encode("Trabalhadora@2026"), Role.HOUSEKEEPER));
		HousekeeperProfile worker = housekeepers.saveAndFlush(new HousekeeperProfile(workerAccount,
				java.time.LocalDate.of(1990, 1, 1), "84" + suffix.substring(0, 7), "Maputo", "KaMpfumu",
				"Polana", Set.of("CLEANING"), Set.of("MON"), 9000, 5));
		HireRequest request = requests.saveAndFlush(new HireRequest(client, worker, Set.of("CLEANING"),
				Set.of("MON"), java.time.LocalDate.now().plusDays(1), "", "Limpeza semanal", 9000, 6, 8));
		EmploymentContract contract = new EmploymentContract(request, "Minuta para assinatura", false);
		contract.reviewByAgency();
		contract = contracts.saveAndFlush(contract);

		String clientToken = login(client.getEmail(), "Cliente@2026");
		mvc.perform(patch("/api/contracts/" + contract.getId() + "/sign")
						.header("Authorization", "Bearer " + clientToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(mapper.writeValueAsString(Map.of("fullName", "Cliente Exacto ", "contractRead", true))))
				.andExpect(status().isBadRequest());
		mvc.perform(patch("/api/contracts/" + contract.getId() + "/sign")
						.header("Authorization", "Bearer " + clientToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(mapper.writeValueAsString(Map.of("fullName", "Cliente Exacto", "contractRead", false))))
				.andExpect(status().isBadRequest());
		mvc.perform(patch("/api/contracts/" + contract.getId() + "/sign")
						.header("Authorization", "Bearer " + clientToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(mapper.writeValueAsString(Map.of("fullName", "Cliente Exacto", "contractRead", true))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.clientSignatureName").value("Cliente Exacto"))
				.andExpect(jsonPath("$.workerSigned").value(false));

		String workerToken = login(workerAccount.getEmail(), "Trabalhadora@2026");
		mvc.perform(patch("/api/contracts/" + contract.getId() + "/sign")
						.header("Authorization", "Bearer " + workerToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(mapper.writeValueAsString(Map.of("fullName", "Amélia", "contractRead", true))))
				.andExpect(status().isBadRequest());
		mvc.perform(patch("/api/contracts/" + contract.getId() + "/sign")
						.header("Authorization", "Bearer " + workerToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(mapper.writeValueAsString(Map.of("fullName", "Amélia João", "contractRead", true))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.workerSignatureName").value("Amélia João"))
				.andExpect(jsonPath("$.clientSigned").value(true))
				.andExpect(jsonPath("$.workerSigned").value(true))
				.andExpect(jsonPath("$.status").value("ACTIVE"));
		mvc.perform(get("/api/contracts").header("Authorization", "Bearer " + clientToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].status").value("ACTIVE"))
				.andExpect(jsonPath("$[0].clientSignatureName").value("Cliente Exacto"))
				.andExpect(jsonPath("$[0].workerSignatureName").value("Amélia João"));
		mvc.perform(get("/api/contracts").header("Authorization", "Bearer " + workerToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].status").value("ACTIVE"))
				.andExpect(jsonPath("$[0].clientSigned").value(true))
				.andExpect(jsonPath("$[0].workerSigned").value(true));
		org.junit.jupiter.api.Assertions.assertEquals(1, notifications.findByAccountIdOrderByCreatedAtDesc(client.getId()).stream()
				.filter(notification -> notification.getEventType().equals("CONTRACT_ACTIVE")).count());
		org.junit.jupiter.api.Assertions.assertEquals(1, notifications.findByAccountIdOrderByCreatedAtDesc(workerAccount.getId()).stream()
				.filter(notification -> notification.getEventType().equals("CONTRACT_ACTIVE")).count());
	}

	@Test
	void workStartWaitsForBothPartiesAndCompletesWhenTheSecondConfirms() throws Exception {
		String suffix = UUID.randomUUID().toString();
		Account client = accounts.saveAndFlush(new Account("Cliente Início", "client-start-" + suffix + "@test.local", encoder.encode("Cliente@2026"), Role.CLIENT));
		Account workerAccount = accounts.saveAndFlush(new Account("Trabalhadora Início", "worker-start-" + suffix + "@test.local", encoder.encode("Trabalhadora@2026"), Role.HOUSEKEEPER));
		Account agent = accounts.saveAndFlush(new Account("Agente Início", "agent-start-" + suffix + "@test.local", encoder.encode("Agente@2026"), Role.AGENT));
		HousekeeperProfile worker = housekeepers.saveAndFlush(new HousekeeperProfile(workerAccount,
				java.time.LocalDate.of(1990, 1, 1), "86" + suffix.substring(0, 7), "Maputo", "KaMpfumu",
				"Polana", Set.of("CLEANING"), Set.of("MON"), 9000, 5));
		java.time.LocalDate simulatedStartDate = java.time.LocalDate.now(java.time.ZoneId.of("Africa/Maputo")).plusDays(2);
		HireRequest request = requests.saveAndFlush(new HireRequest(client, worker, Set.of("CLEANING"),
				Set.of("MON"), simulatedStartDate, "", "Limpeza semanal", 9000, 6, 8));
		EmploymentContract contract = new EmploymentContract(request, "Minuta para início", false);
		contract.reviewByAgency();
		contract = contracts.saveAndFlush(contract);

		String clientToken = login(client.getEmail(), "Cliente@2026");
		String workerToken = login(workerAccount.getEmail(), "Trabalhadora@2026");
		String agentToken = login(agent.getEmail(), "Agente@2026");
		mvc.perform(put("/api/agent/settings/test-date")
						.header("Authorization", "Bearer " + agentToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(mapper.writeValueAsString(Map.of("date", simulatedStartDate.toString()))))
				.andExpect(status().isOk());
		mvc.perform(patch("/api/contracts/" + contract.getId() + "/sign")
						.header("Authorization", "Bearer " + clientToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(mapper.writeValueAsString(Map.of("fullName", "Cliente Início", "contractRead", true))))
				.andExpect(status().isOk());
		mvc.perform(patch("/api/contracts/" + contract.getId() + "/sign")
						.header("Authorization", "Bearer " + workerToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(mapper.writeValueAsString(Map.of("fullName", "Trabalhadora Início", "contractRead", true))))
				.andExpect(status().isOk());

		mvc.perform(patch("/api/contracts/" + contract.getId() + "/started")
						.header("Authorization", "Bearer " + clientToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.clientConfirmedStart").value(true))
				.andExpect(jsonPath("$.workerConfirmedStart").value(false))
				.andExpect(jsonPath("$.workStarted").value(false));
		mvc.perform(patch("/api/contracts/" + contract.getId() + "/started")
						.header("Authorization", "Bearer " + workerToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.clientConfirmedStart").value(true))
				.andExpect(jsonPath("$.workerConfirmedStart").value(true))
				.andExpect(jsonPath("$.workStarted").value(true));
		mvc.perform(get("/api/contracts").header("Authorization", "Bearer " + clientToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].workStarted").value(true));
		org.junit.jupiter.api.Assertions.assertEquals(1, notifications.findByAccountIdOrderByCreatedAtDesc(client.getId()).stream()
				.filter(notification -> notification.getEventType().equals("WORK_START_CONFIRMED")).count());
		org.junit.jupiter.api.Assertions.assertEquals(1, notifications.findByAccountIdOrderByCreatedAtDesc(workerAccount.getId()).stream()
				.filter(notification -> notification.getEventType().equals("WORK_START_CONFIRMED")).count());
		mvc.perform(put("/api/agent/settings/test-date")
						.header("Authorization", "Bearer " + agentToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"date\":null}"))
				.andExpect(status().isOk());
	}

	@Test
	void agencyCanSetAndClearTestDateButClientsCannot() throws Exception {
		String suffix = UUID.randomUUID().toString();
		Account agent = accounts.saveAndFlush(new Account("Agente Data", "agent-date-" + suffix + "@test.local", encoder.encode("Agente@2026"), Role.AGENT));
		Account client = accounts.saveAndFlush(new Account("Cliente Data", "client-date-" + suffix + "@test.local", encoder.encode("Cliente@2026"), Role.CLIENT));
		String agentToken = login(agent.getEmail(), "Agente@2026");
		String clientToken = login(client.getEmail(), "Cliente@2026");
		java.time.LocalDate testDate = java.time.LocalDate.of(2026, 11, 21);

		mvc.perform(get("/api/time").header("Authorization", "Bearer " + clientToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.serverDate").isNotEmpty())
				.andExpect(jsonPath("$.testDateEnabled").value(true));
		mvc.perform(put("/api/agent/settings/test-date")
						.header("Authorization", "Bearer " + clientToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(mapper.writeValueAsString(Map.of("date", testDate.toString()))))
				.andExpect(status().isForbidden());
		mvc.perform(put("/api/agent/settings/test-date")
						.header("Authorization", "Bearer " + agentToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(mapper.writeValueAsString(Map.of("date", testDate.toString()))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.serverDate").value(testDate.toString()))
				.andExpect(jsonPath("$.testDateOverride").value(testDate.toString()));
		mvc.perform(put("/api/agent/settings/test-date")
						.header("Authorization", "Bearer " + agentToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"date\":null}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.testDateOverride").value(""));
	}

	@Test
	void absenceListCanLoadLazyContractDetails() throws Exception {
		String suffix = UUID.randomUUID().toString();
		Account client = accounts.saveAndFlush(new Account("Cliente Falta", "client-absence-" + suffix + "@test.local", encoder.encode("Cliente@2026"), Role.CLIENT));
		Account workerAccount = accounts.saveAndFlush(new Account("Trabalhadora Falta", "worker-absence-" + suffix + "@test.local", encoder.encode("Trabalhadora@2026"), Role.HOUSEKEEPER));
		Account agent = accounts.saveAndFlush(new Account("Agente Falta", "agent-absence-" + suffix + "@test.local", encoder.encode("Agente@2026"), Role.AGENT));
		HousekeeperProfile worker = housekeepers.saveAndFlush(new HousekeeperProfile(workerAccount,
				java.time.LocalDate.of(1990, 1, 1), "87" + suffix.substring(0, 7), "Maputo", "KaMpfumu",
				"Polana", Set.of("CLEANING"), Set.of("MON"), 9000, 5));
		HireRequest request = requests.saveAndFlush(new HireRequest(client, worker, Set.of("CLEANING"),
				Set.of("MON"), java.time.LocalDate.now(), "", "Limpeza semanal", 9000, 6, 8));
		EmploymentContract contract = contracts.saveAndFlush(new EmploymentContract(request, "Minuta de falta", false));
		AbsenceRecord absence = absences.saveAndFlush(new AbsenceRecord(contract, client, java.time.LocalDate.now(), new java.math.BigDecimal("1.00"), "Teste"));
		String token = login(client.getEmail(), "Cliente@2026");
		String agentToken = login(agent.getEmail(), "Agente@2026");

		mvc.perform(get("/api/absences").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].clientName").value("Cliente Falta"))
				.andExpect(jsonPath("$[0].workerName").value("Trabalhadora Falta"))
				.andExpect(jsonPath("$[0].contractId").value(contract.getId().toString()));
		mvc.perform(patch("/api/agent/absences/" + absence.getId() + "/decision?decision=JUSTIFICADA&note=Analisada")
						.header("Authorization", "Bearer " + agentToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("JUSTIFICADA"))
				.andExpect(jsonPath("$.decisionNote").value("Analisada"));
	}

	@Test
	void workerCanSubmitTextOnlyJustificationOrDeclareHavingNone() throws Exception {
		String suffix = UUID.randomUUID().toString();
		Account client = accounts.saveAndFlush(new Account("Cliente Justificação", "client-justification-" + suffix + "@test.local", encoder.encode("Cliente@2026"), Role.CLIENT));
		Account workerAccount = accounts.saveAndFlush(new Account("Trabalhadora Justificação", "worker-justification-" + suffix + "@test.local", encoder.encode("Trabalhadora@2026"), Role.HOUSEKEEPER));
		HousekeeperProfile worker = housekeepers.saveAndFlush(new HousekeeperProfile(workerAccount,
				java.time.LocalDate.of(1990, 1, 1), "88" + suffix.substring(0, 7), "Maputo", "KaMpfumu",
				"Polana", Set.of("CLEANING"), Set.of("MON"), 9000, 5));
		HireRequest request = requests.saveAndFlush(new HireRequest(client, worker, Set.of("CLEANING"),
				Set.of("MON"), java.time.LocalDate.now(), "", "Limpeza semanal", 9000, 6, 8));
		EmploymentContract contract = contracts.saveAndFlush(new EmploymentContract(request, "Minuta de falta", false));
		AbsenceRecord withExplanation = absences.saveAndFlush(new AbsenceRecord(contract, client, java.time.LocalDate.now(), new java.math.BigDecimal("1.00"), "Teste"));
		AbsenceRecord withoutExplanation = absences.saveAndFlush(new AbsenceRecord(contract, client, java.time.LocalDate.now(), new java.math.BigDecimal("1.00"), "Teste"));
		String workerToken = login(workerAccount.getEmail(), "Trabalhadora@2026");

		MockMultipartFile text = new MockMultipartFile("text", "", "text/plain", "Estava doente.".getBytes(java.nio.charset.StandardCharsets.UTF_8));
		mvc.perform(multipart("/api/absences/" + withExplanation.getId() + "/justification")
						.file(text)
						.header("Authorization", "Bearer " + workerToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("JUSTIFICACAO_SUBMETIDA"))
				.andExpect(jsonPath("$.justification").value("Estava doente."));
		mvc.perform(patch("/api/absences/" + withoutExplanation.getId() + "/no-justification")
						.header("Authorization", "Bearer " + workerToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("SEM_JUSTIFICACAO"))
				.andExpect(jsonPath("$.justification").value("A trabalhadora declarou não possuir justificação."));
	}

	@Test
	void agencyCanLoadAdditionalWorkWithLazyCreatorAndContract() throws Exception {
		String suffix = UUID.randomUUID().toString();
		Account client = accounts.saveAndFlush(new Account("Cliente Extra", "client-extra-" + suffix + "@test.local", encoder.encode("Cliente@2026"), Role.CLIENT));
		Account workerAccount = accounts.saveAndFlush(new Account("Trabalhadora Extra", "worker-extra-" + suffix + "@test.local", encoder.encode("Trabalhadora@2026"), Role.HOUSEKEEPER));
		Account agent = accounts.saveAndFlush(new Account("Agente Extra", "agent-extra-" + suffix + "@test.local", encoder.encode("Agente@2026"), Role.AGENT));
		HousekeeperProfile worker = housekeepers.saveAndFlush(new HousekeeperProfile(workerAccount,
				java.time.LocalDate.of(1990, 1, 1), "90" + suffix.substring(0, 7), "Maputo", "KaMpfumu",
				"Polana", Set.of("CLEANING"), Set.of("MON"), 9000, 5));
		HireRequest request = requests.saveAndFlush(new HireRequest(client, worker, Set.of("CLEANING"),
				Set.of("MON"), java.time.LocalDate.now().plusDays(1), "", "Limpeza semanal", 9000, 6, 8));
		EmploymentContract contract = contracts.saveAndFlush(new EmploymentContract(request, "Minuta extra", false));
		AdditionalWork extra = new AdditionalWork(contract, client, "Organização de armários",
				java.time.LocalDate.now(), new java.math.BigDecimal("2"), new java.math.BigDecimal("500"), "ADDITIONAL_SERVICE");
		extra.decide("APPROVED", agent, "Aprovado para liquidação");
		additionalWorks.saveAndFlush(extra);
		String agentToken = login(agent.getEmail(), "Agente@2026");

		mvc.perform(get("/api/additional-work")
						.header("Authorization", "Bearer " + agentToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].description").value("Organização de armários"))
				.andExpect(jsonPath("$[0].createdBy").value("Cliente Extra"))
				.andExpect(jsonPath("$[0].contractId").value(contract.getId().toString()));
		mvc.perform(get("/api/contracts/" + contract.getId() + "/statement?period=" + java.time.YearMonth.now())
						.header("Authorization", "Bearer " + agentToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.additionalServices").value(1000.0))
				.andExpect(jsonPath("$.additionalWorkItems[0].description").value("Organização de armários"))
				.andExpect(jsonPath("$.additionalWorkItems[0].quantity").value(2));
	}

	@Test
	@Transactional
	void approvingAdditionalWorkKeepsProposedPriceWhenAgencyRateIsZero() throws Exception {
		String suffix = UUID.randomUUID().toString();
		Account client = accounts.saveAndFlush(new Account("Cliente Preço", "client-price-" + suffix + "@test.local", encoder.encode("Cliente@2026"), Role.CLIENT));
		Account workerAccount = accounts.saveAndFlush(new Account("Trabalhadora Preço", "worker-price-" + suffix + "@test.local", encoder.encode("Trabalhadora@2026"), Role.HOUSEKEEPER));
		Account agent = accounts.saveAndFlush(new Account("Agente Preço", "agent-price-" + suffix + "@test.local", encoder.encode("Agente@2026"), Role.AGENT));
		HousekeeperProfile worker = housekeepers.saveAndFlush(new HousekeeperProfile(workerAccount,
				java.time.LocalDate.of(1990, 1, 1), "92" + suffix.substring(0, 7), "Maputo", "KaMpfumu",
				"Polana", Set.of("CLEANING"), Set.of("MON"), 9000, 5));
		HireRequest request = requests.saveAndFlush(new HireRequest(client, worker, Set.of("CLEANING"),
				Set.of("MON"), java.time.LocalDate.now(), "", "Limpeza semanal", 9000, 6, 8));
		EmploymentContract contract = contracts.saveAndFlush(new EmploymentContract(request, "Minuta preço", false));
		financePolicies.saveAndFlush(new AgencyFinancePolicy());
		AdditionalWork extra = additionalWorks.saveAndFlush(new AdditionalWork(contract, workerAccount,
				"Limpeza profunda", java.time.LocalDate.now(), new java.math.BigDecimal("2"),
				new java.math.BigDecimal("250"), "ADDITIONAL_SERVICE"));
		String agentToken = login(agent.getEmail(), "Agente@2026");

		mvc.perform(patch("/api/agent/additional-work/" + extra.getId() + "?decision=APPROVED")
						.header("Authorization", "Bearer " + agentToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.unitPrice").value(250.0))
				.andExpect(jsonPath("$.total").value(500.0))
				.andExpect(jsonPath("$.status").value("APPROVED"));
	}

	@Test
	void onlyAgencyCanSimulatePaymentAndSimulationIsNotMarkedAsRealPayment() throws Exception {
		String suffix = UUID.randomUUID().toString();
		Account client = accounts.saveAndFlush(new Account("Cliente Pagamento", "client-payment-" + suffix + "@test.local", encoder.encode("Cliente@2026"), Role.CLIENT));
		Account workerAccount = accounts.saveAndFlush(new Account("Trabalhadora Pagamento", "worker-payment-" + suffix + "@test.local", encoder.encode("Trabalhadora@2026"), Role.HOUSEKEEPER));
		Account agent = accounts.saveAndFlush(new Account("Agente Pagamento", "agent-payment-" + suffix + "@test.local", encoder.encode("Agente@2026"), Role.AGENT));
		HousekeeperProfile worker = housekeepers.saveAndFlush(new HousekeeperProfile(workerAccount,
				java.time.LocalDate.of(1990, 1, 1), "89" + suffix.substring(0, 7), "Maputo", "KaMpfumu",
				"Polana", Set.of("CLEANING"), Set.of("MON"), 9000, 5));
		HireRequest request = requests.saveAndFlush(new HireRequest(client, worker, Set.of("CLEANING"),
				Set.of("MON"), java.time.LocalDate.now(), "", "Limpeza semanal", 9000, 6, 8));
		EmploymentContract contract = contracts.saveAndFlush(new EmploymentContract(request, "Minuta de pagamento", false));
		PaymentObligation obligation = payments.saveAndFlush(new PaymentObligation(contract, "2026-11",
				new java.math.BigDecimal("9000.00"), java.time.LocalDate.of(2026, 12, 5)));
		String agentToken = login(agent.getEmail(), "Agente@2026");
		String clientToken = login(client.getEmail(), "Cliente@2026");

		mvc.perform(post("/api/agent/payment-obligations/" + obligation.getId() + "/simulate-payment")
						.header("Authorization", "Bearer " + clientToken))
				.andExpect(status().isForbidden());
		mvc.perform(post("/api/agent/payment-obligations/" + obligation.getId() + "/simulate-payment")
						.header("Authorization", "Bearer " + agentToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("SIMULADO"))
				.andExpect(jsonPath("$.provider").value("SIMULAÇÃO LOCAL"));
	}

	@Test
	void monthlyPaymentObligationCanOnlyBeCreatedOncePerContractAndPeriod() throws Exception {
		String suffix = UUID.randomUUID().toString();
		Account client = accounts.saveAndFlush(new Account("Cliente Obrigação", "client-obligation-" + suffix + "@test.local", encoder.encode("Cliente@2026"), Role.CLIENT));
		Account workerAccount = accounts.saveAndFlush(new Account("Trabalhadora Obrigação", "worker-obligation-" + suffix + "@test.local", encoder.encode("Trabalhadora@2026"), Role.HOUSEKEEPER));
		HousekeeperProfile worker = housekeepers.saveAndFlush(new HousekeeperProfile(workerAccount,
				java.time.LocalDate.of(1990, 1, 1), "91" + suffix.substring(0, 7), "Maputo", "KaMpfumu",
				"Polana", Set.of("CLEANING"), Set.of("MON"), 9000, 5));
		HireRequest request = requests.saveAndFlush(new HireRequest(client, worker, Set.of("CLEANING"),
				Set.of("MON"), java.time.LocalDate.now(), "", "Limpeza semanal", 9000, 6, 8));
		EmploymentContract contract = new EmploymentContract(request, "Minuta obrigação", false);
		contract.signClient(client.getName());
		contract.signWorker(workerAccount.getName());
		contracts.saveAndFlush(contract);
		String clientToken = login(client.getEmail(), "Cliente@2026");

		MvcResult first = mvc.perform(post("/api/contracts/" + contract.getId() + "/payment-obligations?period=2026-11")
						.header("Authorization", "Bearer " + clientToken))
				.andExpect(status().isCreated())
				.andReturn();
		String firstId = mapper.readTree(first.getResponse().getContentAsString()).path("id").asString();
		mvc.perform(post("/api/contracts/" + contract.getId() + "/payment-obligations?period=2026-11")
						.header("Authorization", "Bearer " + clientToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(firstId));

		assertEquals(firstId, payments.findByContractIdAndReferenceMonth(contract.getId(), "2026-11").orElseThrow().getId().toString());
	}

	private String login(String email, String password) throws Exception {
		MvcResult result = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content(mapper.writeValueAsString(Map.of("email", email, "password", password))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.token").isNotEmpty()).andReturn();
		return mapper.readTree(result.getResponse().getContentAsString()).path("token").asString();
	}

	private Account accountWithPhoto(String name, String email, Role role) {
		Account account = new Account(name, email, encoder.encode("Cliente@2026"), role);
		account.setProfilePhoto(new byte[]{(byte) 137, 80, 78, 71, 13, 10, 26, 10}, "image/png");
		return account;
	}

}
