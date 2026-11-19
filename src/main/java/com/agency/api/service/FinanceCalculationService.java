package com.agency.api.service;

import com.agency.api.domain.*;
import com.agency.api.domain.AgencyFinancePolicy;
import com.agency.api.repository.*;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.math.*;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

@Service
public class FinanceCalculationService {
    private final AbsenceRepository absences;
    private final AdditionalWorkRepository extras;
    private final AgencyFinancePolicyRepository policies;
    private final int configuredDivisor;

    public record AdditionalWorkLine(UUID id, LocalDate date, String workType, String description, BigDecimal quantity,
            BigDecimal unitPrice, BigDecimal total) {
    }

    public record Statement(UUID contractId, String period, BigDecimal baseSalary, BigDecimal overtime,
            BigDecimal additionalServices, BigDecimal workerAbsenceDiscount, BigDecimal clientAbsenceDiscount,
            BigDecimal agencyCompensation, BigDecimal workerNet, BigDecimal clientDue, BigDecimal agencyCost,
            BigDecimal agencyFee, int divisor, List<String> calculationNotes,
            List<AdditionalWorkLine> additionalWorkItems) {
    }

    public FinanceCalculationService(AbsenceRepository absences, AdditionalWorkRepository extras,
            AgencyFinancePolicyRepository policies, @Value("${app.finance.monthly-divisor:30}") int divisor) {
        this.absences = absences;
        this.extras = extras;
        this.policies = policies;
        this.configuredDivisor = divisor;
    }

    public Statement calculate(EmploymentContract contract, String period) {
        AgencyFinancePolicy policy = policies.findById("agency").orElse(null);
        int monthlyDivisor = policy == null ? configuredDivisor : policy.getMonthlyDivisor();
        BigDecimal overtimeRate = policy == null ? BigDecimal.ZERO : policy.getOvertimeHourlyRate();
        BigDecimal agencyFeePercent = policy == null ? BigDecimal.TEN : policy.getAgencyFeePercent();
        if (monthlyDivisor < 1)
            throw new IllegalStateException("Divisor financeiro mensal inválido.");
        YearMonth month = YearMonth.parse(period);
        BigDecimal base = BigDecimal.valueOf(contract.getHireRequest().getMonthlySalary()).setScale(2,
                RoundingMode.HALF_UP);
        BigDecimal workerDiscount = BigDecimal.ZERO, clientDiscount = BigDecimal.ZERO,
                agencyCompensation = BigDecimal.ZERO;
        for (AbsenceRecord a : absences.findAll())
            if (Objects.equals(a.getContract().getId(), contract.getId())
                    && YearMonth.from(a.getAbsenceDate()).equals(month)) {
                if (!"JUSTIFICADA".equals(a.getStatus()) && !"NAO_JUSTIFICADA".equals(a.getStatus()))
                    continue;
                BigDecimal discount = base.multiply(a.getDays()).divide(BigDecimal.valueOf(monthlyDivisor), 2,
                        RoundingMode.HALF_UP);
                clientDiscount = clientDiscount.add(discount);
                if ("NAO_JUSTIFICADA".equals(a.getStatus()))
                    workerDiscount = workerDiscount.add(discount);
                else if ("JUSTIFICADA".equals(a.getStatus()))
                    agencyCompensation = agencyCompensation.add(discount);
            }
        var approved = extras.findAll().stream()
                .filter(x -> Objects.equals(x.getContract().getId(), contract.getId())
                        && YearMonth.from(x.getServiceDate()).equals(month) && "APPROVED".equals(x.getStatus()))
                .toList();
        BigDecimal overtime = approved.stream().filter(x -> "OVERTIME".equals(x.getWorkType()))
                .map(AdditionalWork::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal additional = approved.stream().filter(x -> !"OVERTIME".equals(x.getWorkType()))
                .map(AdditionalWork::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        var additionalWorkItems = approved.stream().map(x -> new AdditionalWorkLine(x.getId(), x.getServiceDate(),
                x.getWorkType(), x.getDescription(), x.getQuantity(), x.getUnitPrice(), x.getTotal())).toList();
        workerDiscount = workerDiscount.setScale(2, RoundingMode.HALF_UP);
        clientDiscount = clientDiscount.setScale(2, RoundingMode.HALF_UP);
        BigDecimal workerNet = base.add(overtime).add(additional).subtract(workerDiscount).setScale(2,
                RoundingMode.HALF_UP);
        BigDecimal agencyFee = base.multiply(agencyFeePercent).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal clientDue = base.subtract(clientDiscount).add(overtime).add(additional).add(agencyFee)
                .max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        agencyCompensation = agencyCompensation.setScale(2, RoundingMode.HALF_UP);
        return new Statement(contract.getId(), period, base, overtime, additional, workerDiscount, clientDiscount,
                agencyCompensation, workerNet, clientDue, agencyCompensation, agencyFee, monthlyDivisor,
                List.of("Só faltas decididas pela agência entram no cálculo; justificações pendentes não alteram a liquidação.",
                        "Falta justificada reduz a cobrança do cliente e a agência cobre o desconto sem reduzir o salário da trabalhadora.",
                        "Falta não justificada reduz a cobrança do cliente e o valor líquido da trabalhadora; a agência não cobre essa diferença.",
                        "Horas extraordinárias e serviços adicionais só entram após validação da agência.",
                        "Divisor mensal configurado pela agência: " + monthlyDivisor
                                + " dias. Tarifa horária configurada: " + overtimeRate + " MZN.",
                        "Taxa de agenciamento configurada: " + agencyFeePercent + "%."),
                additionalWorkItems);
    }
}
