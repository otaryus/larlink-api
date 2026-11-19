package com.agency.api.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="finance_adjustments",indexes=@Index(name="idx_finance_adjustment_close",columnList="financial_close_id,created_at"))
public class FinanceAdjustment {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @ManyToOne(optional=false,fetch=FetchType.LAZY) @JoinColumn(name="financial_close_id",nullable=false) private FinancialClose financialClose;
    @ManyToOne(optional=false,fetch=FetchType.LAZY) @JoinColumn(name="created_by",nullable=false) private Account createdBy;
    @Column(nullable=false,precision=12,scale=2) private BigDecimal amount;
    @Column(nullable=false,length=1000) private String reason;
    @Column(nullable=false) private Instant createdAt=Instant.now();
    protected FinanceAdjustment(){}
    public FinanceAdjustment(FinancialClose close,Account actor,BigDecimal amount,String reason){financialClose=close;createdBy=actor;this.amount=amount;this.reason=reason;}
    public UUID getId(){return id;} public FinancialClose getFinancialClose(){return financialClose;} public Account getCreatedBy(){return createdBy;} public BigDecimal getAmount(){return amount;} public String getReason(){return reason;} public Instant getCreatedAt(){return createdAt;}
}
