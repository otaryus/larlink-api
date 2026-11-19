package com.agency.api.domain;

import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;

@Entity @Table(name="employment_contracts")
public class EmploymentContract {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @OneToOne(optional=false) @JoinColumn(nullable=false,unique=true) private HireRequest hireRequest;
    @Column(nullable=false,columnDefinition="text") private String contractText;
    @Column(nullable=false) private boolean aiGenerated;
    @Column(nullable=false) private String status="AWAITING_SIGNATURES";
    @Column(nullable=false) private boolean clientSigned;
    @Column(nullable=false) private boolean workerSigned;
    @Column(length=150) private String clientSignatureName;
    @Column(length=150) private String workerSignatureName;
    @Column(nullable=false) private boolean agencyReviewed;
    @Column(nullable=false,length=24,columnDefinition="varchar(24) default 'PENDING'") private String agencyReviewState="PENDING";
    @Column(length=1000) private String agencyReviewNote;
    @Column(nullable=false) private boolean clientConfirmedStart;
    @Column(nullable=false) private boolean workerConfirmedStart;
    @Column(nullable=false) private Instant createdAt=Instant.now();
    private Instant clientSignedAt; private Instant workerSignedAt; private Instant agencyReviewedAt; private Instant terminatedAt;
    @Column(length=1000) private String terminationReason;
    protected EmploymentContract() {}
    public EmploymentContract(HireRequest hireRequest,String contractText,boolean aiGenerated){this.hireRequest=hireRequest;this.contractText=contractText;this.aiGenerated=aiGenerated;}
    public UUID getId(){return id;} public HireRequest getHireRequest(){return hireRequest;} public String getContractText(){return contractText;} public boolean isAiGenerated(){return aiGenerated;} public String getStatus(){return status;} public boolean isClientSigned(){return clientSigned;} public boolean isWorkerSigned(){return workerSigned;} public String getClientSignatureName(){return clientSignatureName;} public String getWorkerSignatureName(){return workerSignatureName;} public boolean isAgencyReviewed(){return agencyReviewed;} public String getAgencyReviewState(){return agencyReviewState;} public String getAgencyReviewNote(){return agencyReviewNote;} public boolean isClientConfirmedStart(){return clientConfirmedStart;} public boolean isWorkerConfirmedStart(){return workerConfirmedStart;} public boolean isWorkStarted(){return clientConfirmedStart&&workerConfirmedStart;} public Instant getCreatedAt(){return createdAt;} public Instant getClientSignedAt(){return clientSignedAt;} public Instant getWorkerSignedAt(){return workerSignedAt;} public Instant getAgencyReviewedAt(){return agencyReviewedAt;} public Instant getTerminatedAt(){return terminatedAt;} public String getTerminationReason(){return terminationReason;}
    public void reviseDraft(String text){if(clientSigned||workerSigned||!"AWAITING_SIGNATURES".equals(status))throw new IllegalStateException("O contrato já foi formalizado; crie uma adenda auditável.");contractText=text;agencyReviewed=false;agencyReviewState="PENDING";agencyReviewNote=null;agencyReviewedAt=null;}
    public void signClient(String fullName){if("TERMINATED".equals(status))throw new IllegalStateException("Contrato encerrado.");if(clientSigned)throw new IllegalStateException("O cliente já assinou este contrato.");clientSigned=true;clientSignatureName=fullName;clientSignedAt=Instant.now();updateStatus();} public void signWorker(String fullName){if("TERMINATED".equals(status))throw new IllegalStateException("Contrato encerrado.");if(workerSigned)throw new IllegalStateException("A trabalhadora já assinou este contrato.");workerSigned=true;workerSignatureName=fullName;workerSignedAt=Instant.now();updateStatus();} private void updateStatus(){if(clientSigned&&workerSigned)status="ACTIVE";}
    public void confirmStartedByClient(){clientConfirmedStart=true;}
    public void confirmStartedByWorker(){workerConfirmedStart=true;}
    public void reviewByAgency(){agencyReviewed=true;agencyReviewState="APPROVED";agencyReviewNote=null;agencyReviewedAt=Instant.now();}
    public void requestAgencyChanges(String note){if(clientSigned||workerSigned||!"AWAITING_SIGNATURES".equals(status))throw new IllegalStateException("Só minutas ainda não assinadas podem voltar para alteração.");agencyReviewed=false;agencyReviewState="CHANGES_REQUESTED";agencyReviewNote=note;agencyReviewedAt=Instant.now();}
    public void terminate(String reason){if(!"ACTIVE".equals(status))throw new IllegalStateException("Só contratos activos podem ser encerrados.");status="TERMINATED";terminatedAt=Instant.now();terminationReason=reason;}
}
