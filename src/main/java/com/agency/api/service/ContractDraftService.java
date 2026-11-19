package com.agency.api.service;

import com.agency.api.domain.HireRequest;
import com.agency.api.repository.AgencyFinancePolicyRepository;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class ContractDraftService {

    private static final Logger log = LoggerFactory.getLogger(ContractDraftService.class);
    private final String apiKey, model;
    private final ObjectMapper mapper;
    private final HttpClient http;
    private final AgencyFinancePolicyRepository policies;

    public record Draft(String text, boolean aiGenerated) {}

    public ContractDraftService(ObjectMapper mapper, AgencyFinancePolicyRepository policies, @Value("${app.ai.api-key:}") String apiKey, @Value("${app.ai.model:gpt-6-astra}") String model) {
        this.mapper = mapper;
        this.apiKey = apiKey;
        this.model = model;
        this.policies = policies;
        http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    public Draft generate(HireRequest request) {
        if (apiKey.isBlank()) {
            return new Draft(localDraft(request), false);
        }
        try {
            return new Draft(generateWithAi(request), true);
        } catch (Exception e) {
            log.warn("AI contract generation unavailable; using local draft template: {}", e.getMessage());
            return new Draft(localDraft(request), false);
        }
    }

    private String generateWithAi(HireRequest r) throws Exception {
        var fee = policies.findById("agency").map(p -> p.getAgencyFeePercent()).orElse(java.math.BigDecimal.TEN);
        String system = "Redige uma minuta simples de contrato de trabalho doméstico em português de Moçambique. Usa apenas os dados fornecidos; não inventes leis, direitos, montantes ou condições. Mantém integralmente o escopo de tarefas acordado e separa tarefas regulares de tarefas extraordinárias. Inclui identificação da agência, cliente e trabalhadora, salário, taxa de agenciamento da agência, tarefas, horário, local, início, duração e renovação, faltas e justificação, horas extraordinárias, serviços adicionais, condições de pagamento, direitos e responsabilidades, rescisão, campos de assinatura e revisão obrigatória da agência. Se algum dado estiver ausente, marca-o como [A DEFINIR COM A AGÊNCIA]. O resultado é uma minuta para revisão, não parecer jurídico.";
        String input = "Cliente: " + r.getClient().getName() + ". Empregada: " + r.getHousekeeper().getName() + ". Local: bairro " + r.getNeighborhood() + ", distrito " + r.getDistrict() + ", " + r.getHousekeeper().getCity() + ". Início: " + r.getStartDate() + ". Dias: " + String.join(", ", r.getRequiredDays()) + ". Horário: " + r.getStartTime() + "–" + r.getEndTime() + " (" + r.getHoursPerDay() + " horas por dia). Salário mensal em MZN: " + r.getMonthlySalary() + ". Taxa de agenciamento em percentagem: " + fee + "%. Duração em meses: " + r.getDurationMonths() + ". Competências: " + String.join(", ", r.getRequiredSkills()) + ". Escopo completo definido pelo cliente:\n" + r.getTaskScope() + "\nObservações: " + (r.getNotes() == null ? "" : r.getNotes());
        Map<String, Object> body = Map.of("model", model, "store", false, "input", List.of(Map.of("role", "developer", "content", system), Map.of("role", "user", "content", input)));
        HttpRequest call = HttpRequest.newBuilder(URI.create("https://api.openai.com/v1/responses")).timeout(Duration.ofSeconds(45)).header("Authorization", "Bearer " + apiKey).header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build();
        HttpResponse<String> response = http.send(call, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("OpenAI API returned " + response.statusCode());
        }
        JsonNode root = mapper.readTree(response.body());
        StringBuilder output = new StringBuilder();
        for (JsonNode item : root.path("output")) {
            for (JsonNode content : item.path("content")) {
                if (content.path("type").asText().equals("output_text")) {
                    output.append(content.path("text").asText());
                }
            }
        }
        if (output.isEmpty()) {
            throw new IllegalStateException("AI returned no contract text");
        }
        return output.toString().trim();
    }

    private String localDraft(HireRequest r) {
        String days = String.join(", ", r.getRequiredDays());
        String tasks = r.getTaskScope().lines().map(s -> "- " + s.trim()).reduce((a, b) -> a + "\n" + b).orElse("- [A DEFINIR COM A AGÊNCIA]");
        var fee = policies.findById("agency").map(p -> p.getAgencyFeePercent()).orElse(java.math.BigDecimal.TEN);
        return "MINUTA DE CONTRATO DE TRABALHO DOMÉSTICO\n\nDOCUMENTO GERADO AUTOMATICAMENTE. REVISÃO DA AGÊNCIA E DAS PARTES NECESSÁRIA ANTES DA ASSINATURA.\n\n"
                + "1. IDENTIFICAÇÃO DAS PARTES\n"
                + "Agência LarLink: [DADOS LEGAIS A DEFINIR PELA AGÊNCIA].\n"
                + "Cliente/Empregador(a): " + r.getClient().getName() + ".\n"
                + "Trabalhadora: " + r.getHousekeeper().getName() + ".\n\n"
                + "2. LOCAL, INÍCIO E DURAÇÃO\n"
                + "Local de trabalho: bairro " + (r.getNeighborhood()==null?"[A DEFINIR]":r.getNeighborhood()) + ", distrito " + (r.getDistrict()==null?"[A DEFINIR]":r.getDistrict()) + ", " + r.getHousekeeper().getCity() + ". Início previsto: " + r.getStartDate().format(DateTimeFormatter.ISO_LOCAL_DATE)
                + ". Duração: " + r.getDurationMonths() + " meses. Renovação e aviso prévio: [A DEFINIR COM A AGÊNCIA E AS PARTES].\n\n"
                + "3. HORÁRIO DE TRABALHO\n"
                + "Dias acordados: " + days + ". Horário: " + (r.getStartTime()==null?"[A DEFINIR]":r.getStartTime()) + "–" + (r.getEndTime()==null?"[A DEFINIR]":r.getEndTime()) + " (" + r.getHoursPerDay() + " horas por dia). Pausas e descanso semanal: [A DEFINIR ANTES DA ASSINATURA].\n\n"
                + "4. REMUNERAÇÃO E PAGAMENTO\n"
                + "Salário mensal acordado: " + r.getMonthlySalary() + " MZN.\n"
                + "Taxa de agenciamento LarLink: " + fee + "% sobre o salário acordado. Data, canal e condições de pagamento: [A DEFINIR COM A AGÊNCIA].\n\n"
                + "5. TAREFAS E RESPONSABILIDADES\n"
                + "Escopo acordado pelo cliente:\n" + tasks + "\n"
                + "Responsabilidades específicas do cliente e da trabalhadora: [A REVER PELAS PARTES E AGÊNCIA].\n\n"
                + "6. FALTAS E JUSTIFICAÇÕES\n"
                + "A falta deve ser registada na plataforma. A trabalhadora poderá submeter texto e comprovativo; a agência regista a decisão e o respectivo impacto financeiro de acordo com a política configurada e legislação aplicável.\n\n"
                + "7. HORAS EXTRAORDINÁRIAS E SERVIÇOS ADICIONAIS\n"
                + "Devem ser previamente descritos, comunicados às partes e aprovados pela agência antes da cobrança. Tarifas e limites: [A DEFINIR E APROVAR].\n\n"
                + "8. CONDIÇÕES DE PAGAMENTO\n"
                + "As obrigações e confirmações de pagamento dependem de integração com prestador autorizado. A entidade, referência, vencimento e valor confirmados constarão do registo mensal da plataforma.\n\n"
                + "9. DIREITOS, RESPONSABILIDADES E RESCISÃO\n"
                + "Direitos, deveres, condições de rescisão, aviso e tratamento de dados: [REVISÃO JURÍDICA E PREENCHIMENTO PELA AGÊNCIA ANTES DA FORMALIZAÇÃO].\n\n"
                + "10. OCORRÊNCIAS E MEDIAÇÃO\n"
                + "Qualquer parte pode comunicar incumprimentos à agência pelo canal confidencial da plataforma. A agência acompanhará a ocorrência e registará a decisão.\n\n"
                + "11. REVISÃO, VERSÕES E ASSINATURAS\n"
                + "Esta minuta não substitui a revisão jurídica. Alterações após assinatura exigem adenda versionada; não será feita alteração silenciosa ao texto formalizado.\n\n"
                + "Empregador(a): ____________________  Data: __________\n"
                + "Trabalhadora: _____________________  Data: __________\n"
                + "Agência (revisão): _________________  Data: __________\n\n"
                + "Observações do pedido: " + (r.getNotes() == null || r.getNotes().isBlank() ? "Nenhuma." : r.getNotes());
    }
}
