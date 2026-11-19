package com.agency.api.service;

import com.agency.api.domain.*;
import com.agency.api.repository.*;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;

@Component
public class MonthlyLifecycleScheduler {
    private final ContractRepository contracts; private final PaymentObligationRepository payments; private final FinanceCalculationService finance; private final HireRequestRepository requests; private final AgencyFinancePolicyRepository policies;
    public MonthlyLifecycleScheduler(ContractRepository contracts,PaymentObligationRepository payments,FinanceCalculationService finance,HireRequestRepository requests,AgencyFinancePolicyRepository policies){this.contracts=contracts;this.payments=payments;this.finance=finance;this.requests=requests;this.policies=policies;}
    @Scheduled(cron="${app.payments.obligation-cron:0 0 2 1 * *}",zone="Africa/Maputo") @Transactional public void generateMonthlyObligations(){var today=LocalDate.now(ZoneId.of("Africa/Maputo"));YearMonth current=YearMonth.from(today),period=current.minusMonths(1);int dueDay=policies.findById("agency").map(AgencyFinancePolicy::getPaymentDueDay).orElse(5);for(var c:contracts.findAllByOrderByCreatedAtDesc()){if(!"ACTIVE".equals(c.getStatus())||payments.existsByContractIdAndReferenceMonth(c.getId(),period.toString()))continue;var statement=finance.calculate(c,period.toString());payments.save(new PaymentObligation(c,period.toString(),statement.clientDue(),current.atDay(Math.min(dueDay,current.lengthOfMonth()))));}}
    @Scheduled(cron="${app.lifecycle.expiry-cron:0 15 3 * * *}",zone="Africa/Maputo") @Transactional public void expirePastObligations(){LocalDate today=LocalDate.now(ZoneId.of("Africa/Maputo"));payments.findAll().forEach(p->p.expireIfDue(today));}
    @Scheduled(cron="${app.lifecycle.request-expiry-cron:0 30 3 * * *}",zone="Africa/Maputo") @Transactional public void expireStaleRequests(){Instant now=Instant.now();requests.findAll().forEach(r->r.expireIfDue(now));}
}
