package com.agency.api.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;

@Entity @Table(name="payment_obligations",uniqueConstraints=@UniqueConstraint(name="uk_obligation_contract_period",columnNames={"contract_id","reference_month"}),indexes=@Index(name="idx_obligation_status_due",columnList="status,due_date"))
public class PaymentObligation {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @ManyToOne(optional=false,fetch=FetchType.LAZY) @JoinColumn(name="contract_id",nullable=false) private EmploymentContract contract;
    @Column(name="reference_month",nullable=false,length=7) private String referenceMonth;
    @Column(nullable=false,precision=12,scale=2) private BigDecimal amount;
    @Column(length=30) private String entityCode;
    @Column(length=40) private String referenceCode;
    @Column(name="due_date",nullable=false) private LocalDate dueDate;
    @Column(nullable=false,length=24) private String status="PENDENTE";
    @Column(length=100) private String provider;
    @Column(length=100,unique=true) private String providerTransactionId;
    @Column private Instant paidAt;
    protected PaymentObligation(){}
    public PaymentObligation(EmploymentContract contract,String month,BigDecimal amount,LocalDate due){this.contract=contract;referenceMonth=month;this.amount=amount;dueDate=due;}
    public UUID getId(){return id;} public EmploymentContract getContract(){return contract;} public String getReferenceMonth(){return referenceMonth;} public BigDecimal getAmount(){return amount;} public String getEntityCode(){return entityCode;} public String getReferenceCode(){return referenceCode;} public LocalDate getDueDate(){return dueDate;} public String getStatus(){return status;} public String getProvider(){return provider;} public Instant getPaidAt(){return paidAt;}
    public void assignProviderReference(String provider,String entity,String reference){this.provider=provider;entityCode=entity;referenceCode=reference;status="PENDENTE";}
    public boolean confirmPaid(String transaction,BigDecimal paidAmount){if("PAGO".equals(status))return false;if("SIMULADO".equals(status))throw new IllegalStateException("Uma obrigação simulada não pode ser confirmada como pagamento real.");if(paidAmount.compareTo(amount)!=0)throw new IllegalArgumentException("O valor confirmado não corresponde à obrigação.");providerTransactionId=transaction;status="PAGO";paidAt=Instant.now();return true;}
    public void simulatePaid(String transaction){if(!"PENDENTE".equals(status))throw new IllegalStateException("Só obrigações pendentes podem ser simuladas.");provider="SIMULAÇÃO LOCAL";providerTransactionId=transaction;status="SIMULADO";paidAt=Instant.now();}
    public void expireIfDue(LocalDate today){if("PENDENTE".equals(status)&&dueDate.isBefore(today))status="EXPIRADO";}
}
