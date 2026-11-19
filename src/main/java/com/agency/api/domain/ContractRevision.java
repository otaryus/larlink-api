package com.agency.api.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="contract_revisions",uniqueConstraints=@UniqueConstraint(name="uk_contract_revision_number",columnNames={"contract_id","revision_number"}),indexes=@Index(name="idx_contract_revision_created",columnList="contract_id,created_at"))
public class ContractRevision {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @ManyToOne(optional=false,fetch=FetchType.LAZY) @JoinColumn(name="contract_id",nullable=false) private EmploymentContract contract;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="created_by") private Account createdBy;
    @Column(name="revision_number",nullable=false) private int revisionNumber;
    @Column(nullable=false,length=20) private String kind;
    @Column(nullable=false,columnDefinition="text") private String content;
    @Column(length=500) private String changeReason;
    @Column(nullable=false) private Instant createdAt=Instant.now();
    protected ContractRevision(){}
    public ContractRevision(EmploymentContract contract,Account creator,int version,String kind,String content,String reason){this.contract=contract;createdBy=creator;revisionNumber=version;this.kind=kind;this.content=content;changeReason=reason;}
    public UUID getId(){return id;} public EmploymentContract getContract(){return contract;} public Account getCreatedBy(){return createdBy;} public int getRevisionNumber(){return revisionNumber;} public String getKind(){return kind;} public String getContent(){return content;} public String getChangeReason(){return changeReason;} public Instant getCreatedAt(){return createdAt;}
}
