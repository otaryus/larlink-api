package com.agency.api.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "salary_proposals", indexes = @Index(name = "idx_proposals_request_created", columnList = "hire_request_id, created_at"))
public class SalaryProposal {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY) @JoinColumn(name = "hire_request_id", nullable = false) private HireRequest hireRequest;
    @ManyToOne(optional = false, fetch = FetchType.LAZY) @JoinColumn(name = "author_id", nullable = false) private Account author;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal monthlySalary;
    @Column(nullable = false, length = 20) private String status = "OPEN";
    @Column(length = 500) private String message;
    @Column(nullable = false) private Instant createdAt = Instant.now();
    protected SalaryProposal() {}
    public SalaryProposal(HireRequest request, Account author, BigDecimal salary, String message) { this.hireRequest=request; this.author=author; this.monthlySalary=salary; this.message=message; }
    public UUID getId(){return id;} public HireRequest getHireRequest(){return hireRequest;} public Account getAuthor(){return author;} public BigDecimal getMonthlySalary(){return monthlySalary;} public String getStatus(){return status;} public String getMessage(){return message;} public Instant getCreatedAt(){return createdAt;}
    public void mark(String status){if(!"OPEN".equals(this.status)) throw new IllegalStateException("Proposta já encerrada."); this.status=status;}
}
