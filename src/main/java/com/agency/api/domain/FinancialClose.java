package com.agency.api.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="financial_closes",uniqueConstraints=@UniqueConstraint(name="uk_financial_close_contract_month",columnNames={"contract_id","reference_month"}))
public class FinancialClose {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @ManyToOne(optional=false,fetch=FetchType.LAZY) @JoinColumn(name="contract_id",nullable=false) private EmploymentContract contract;
    @ManyToOne(optional=false,fetch=FetchType.LAZY) @JoinColumn(name="closed_by",nullable=false) private Account closedBy;
    @Column(name="reference_month",nullable=false,length=7) private String referenceMonth;
    @Column(nullable=false,columnDefinition="text") private String snapshotJson;
    @Column(nullable=false) private Instant closedAt=Instant.now();
    protected FinancialClose(){}
    public FinancialClose(EmploymentContract contract,Account actor,String month,String snapshot){this.contract=contract;closedBy=actor;referenceMonth=month;snapshotJson=snapshot;}
    public UUID getId(){return id;} public EmploymentContract getContract(){return contract;} public Account getClosedBy(){return closedBy;} public String getReferenceMonth(){return referenceMonth;} public String getSnapshotJson(){return snapshotJson;} public Instant getClosedAt(){return closedAt;}
}
