package com.agency.api.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity @Table(name="additional_work",indexes=@Index(name="idx_extra_contract_status",columnList="contract_id,status"))
public class AdditionalWork {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @ManyToOne(optional=false,fetch=FetchType.LAZY) @JoinColumn(name="contract_id",nullable=false) private EmploymentContract contract;
    @ManyToOne(optional=false,fetch=FetchType.LAZY) @JoinColumn(name="created_by",nullable=false) private Account createdBy;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="approved_by") private Account approvedBy;
    @Column(nullable=false,length=500) private String description;
    @Column(nullable=false,length=24) private String workType="ADDITIONAL_SERVICE";
    @Column(nullable=false) private LocalDate serviceDate;
    @Column(nullable=false,precision=8,scale=2) private BigDecimal quantity;
    @Column(nullable=false,precision=12,scale=2) private BigDecimal unitPrice;
    @Column(nullable=false,precision=12,scale=2) private BigDecimal total;
    @Column(nullable=false,length=20) private String status="PENDING_APPROVAL";
    @Column(length=500) private String decisionNote;
    @Column(nullable=false) private Instant createdAt=Instant.now();
    protected AdditionalWork(){}
    public AdditionalWork(EmploymentContract c,Account actor,String desc,LocalDate date,BigDecimal qty,BigDecimal price){this(c,actor,desc,date,qty,price,"ADDITIONAL_SERVICE");}
    public AdditionalWork(EmploymentContract c,Account actor,String desc,LocalDate date,BigDecimal qty,BigDecimal price,String type){contract=c;createdBy=actor;description=desc;serviceDate=date;quantity=qty;unitPrice=price;workType=type;total=qty.multiply(price).setScale(2,java.math.RoundingMode.HALF_UP);}
    public UUID getId(){return id;} public EmploymentContract getContract(){return contract;} public Account getCreatedBy(){return createdBy;} public Account getApprovedBy(){return approvedBy;} public String getDescription(){return description;} public String getWorkType(){return workType;} public LocalDate getServiceDate(){return serviceDate;} public BigDecimal getQuantity(){return quantity;} public BigDecimal getUnitPrice(){return unitPrice;} public BigDecimal getTotal(){return total;} public String getStatus(){return status;} public String getDecisionNote(){return decisionNote;} public Instant getCreatedAt(){return createdAt;}
    public void decide(String status,Account actor,String note,BigDecimal approvedUnitPrice){if(!"PENDING_APPROVAL".equals(this.status))throw new IllegalStateException("Extra já decidido.");if(!java.util.Set.of("APPROVED","REJECTED").contains(status))throw new IllegalArgumentException("Decisão inválida.");if(approvedUnitPrice!=null){unitPrice=approvedUnitPrice;total=quantity.multiply(unitPrice).setScale(2,java.math.RoundingMode.HALF_UP);}this.status=status;approvedBy=actor;decisionNote=note;}
    public void decide(String status,Account actor,String note){decide(status,actor,note,null);}
}
