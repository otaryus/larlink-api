package com.agency.api.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="incident_reports")
public class IncidentReport {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @ManyToOne(optional=false) private EmploymentContract contract;
    @ManyToOne(optional=false) private Account reporter;
    @Column(nullable=false,length=2000) private String description;
    @Column(nullable=false,length=50) private String type="OTHER";
    @Column(nullable=false,length=24) private String status="ABERTA";
    @Column(length=2000) private String agencyDecision;
    @Column(length=80) private String evidenceContentType;
    @Column(columnDefinition="bytea") private byte[] evidence;
    @Column(nullable=false) private Instant createdAt=Instant.now();
    protected IncidentReport() {}
    public IncidentReport(EmploymentContract contract,Account reporter,String description){this(contract,reporter,"OTHER",description);}
    public IncidentReport(EmploymentContract contract,Account reporter,String type,String description){this.contract=contract;this.reporter=reporter;this.type=type;this.description=description;}
    public UUID getId(){return id;} public EmploymentContract getContract(){return contract;} public Account getReporter(){return reporter;} public String getDescription(){return description;} public String getType(){return type;} public String getStatus(){return status;} public String getAgencyDecision(){return agencyDecision;} public byte[] getEvidence(){return evidence;} public String getEvidenceContentType(){return evidenceContentType;} public Instant getCreatedAt(){return createdAt;} public void close(){status="ENCERRADA";} public void updateStatus(String status,String decision){if(!java.util.Set.of("ABERTA","EM_ANALISE","AGUARDA_RESPOSTA","RESOLVIDA","REJEITADA","ENCERRADA").contains(status))throw new IllegalArgumentException("Estado de ocorrência inválido.");this.status=status;agencyDecision=decision;} public void attach(byte[] evidence,String type){this.evidence=evidence;evidenceContentType=type;}
}
