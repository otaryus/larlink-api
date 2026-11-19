package com.agency.api.domain;

import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name="absence_records", indexes=@Index(name="idx_absence_contract_date",columnList="contract_id, absence_date"))
public class AbsenceRecord {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @ManyToOne(optional=false, fetch=FetchType.LAZY) @JoinColumn(name="contract_id",nullable=false) private EmploymentContract contract;
    @ManyToOne(optional=false, fetch=FetchType.LAZY) @JoinColumn(name="reported_by",nullable=false) private Account reportedBy;
    @Column(name="absence_date",nullable=false) private LocalDate absenceDate;
    @Column(nullable=false,precision=5,scale=2) private java.math.BigDecimal days;
    @Column(length=1000) private String notes;
    @Column(nullable=false,length=32) private String status="PENDENTE_JUSTIFICACAO";
    @Column(columnDefinition="text") private String justification;
    @Column(length=80) private String evidenceContentType;
    @Column(columnDefinition="bytea") private byte[] evidence;
    @Column(length=1000) private String agencyDecisionNote;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="reviewed_by") private Account reviewedBy;
    @Column(nullable=false) private Instant createdAt=Instant.now();
    @Column private Instant reviewedAt;
    protected AbsenceRecord(){}
    public AbsenceRecord(EmploymentContract c,Account reporter,LocalDate date,java.math.BigDecimal days,String notes){contract=c;reportedBy=reporter;absenceDate=date;this.days=days;this.notes=notes;}
    public UUID getId(){return id;} public EmploymentContract getContract(){return contract;} public Account getReportedBy(){return reportedBy;} public LocalDate getAbsenceDate(){return absenceDate;} public java.math.BigDecimal getDays(){return days;} public String getNotes(){return notes;} public String getStatus(){return status;} public String getJustification(){return justification;} public byte[] getEvidence(){return evidence;} public String getEvidenceContentType(){return evidenceContentType;} public String getAgencyDecisionNote(){return agencyDecisionNote;} public Instant getCreatedAt(){return createdAt;} public Instant getReviewedAt(){return reviewedAt;}
    public void submitJustification(String text,byte[] bytes,String type){if(!"PENDENTE_JUSTIFICACAO".equals(status))throw new IllegalStateException("A falta não está à espera de justificação.");justification=text;evidence=bytes;evidenceContentType=type;status="JUSTIFICACAO_SUBMETIDA";}
    public void declareNoJustification(){if(!"PENDENTE_JUSTIFICACAO".equals(status))throw new IllegalStateException("A falta não está à espera de uma resposta.");justification="A trabalhadora declarou não possuir justificação.";evidence=null;evidenceContentType=null;status="SEM_JUSTIFICACAO";}
    public void decide(String result,String note,Account reviewer){if(!java.util.Set.of("PENDENTE_JUSTIFICACAO","JUSTIFICACAO_SUBMETIDA","SEM_JUSTIFICACAO","EM_ANALISE").contains(status))throw new IllegalStateException("Esta falta já foi decidida.");if(!java.util.Set.of("JUSTIFICADA","NAO_JUSTIFICADA").contains(result))throw new IllegalArgumentException("Decisão inválida.");status=result;agencyDecisionNote=note;reviewedBy=reviewer;reviewedAt=Instant.now();}
}
