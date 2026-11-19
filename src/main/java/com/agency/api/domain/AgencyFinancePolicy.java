package com.agency.api.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity @Table(name="agency_finance_policy")
public class AgencyFinancePolicy {
    @Id @Column(length=30) private String id="agency";
    @Column(nullable=false) private int monthlyDivisor=30;
    @Column(nullable=false,precision=12,scale=2) private BigDecimal overtimeHourlyRate=BigDecimal.ZERO;
    @Column(nullable=false,precision=12,scale=2) private BigDecimal additionalServiceUnitRate=BigDecimal.ZERO;
    @Column(nullable=false) private int paymentDueDay=5;
    @Column(nullable=false) private int requestValidityDays=14;
    @Column(nullable=false,precision=5,scale=2) private BigDecimal agencyFeePercent=BigDecimal.TEN;
    private LocalDate testDateOverride;
    @Column(nullable=false) private Instant updatedAt=Instant.now();
    public AgencyFinancePolicy(){}
    public String getId(){return id;} public int getMonthlyDivisor(){return monthlyDivisor;} public BigDecimal getOvertimeHourlyRate(){return overtimeHourlyRate;} public BigDecimal getAdditionalServiceUnitRate(){return additionalServiceUnitRate;} public int getPaymentDueDay(){return paymentDueDay;} public int getRequestValidityDays(){return requestValidityDays;} public BigDecimal getAgencyFeePercent(){return agencyFeePercent;} public LocalDate getTestDateOverride(){return testDateOverride;} public Instant getUpdatedAt(){return updatedAt;}
    public void update(int divisor,BigDecimal overtime,BigDecimal additionalRate,int dueDay,int validity,BigDecimal agencyFee){monthlyDivisor=divisor;overtimeHourlyRate=overtime;additionalServiceUnitRate=additionalRate;paymentDueDay=dueDay;requestValidityDays=validity;agencyFeePercent=agencyFee==null?BigDecimal.TEN:agencyFee;updatedAt=Instant.now();}
    public void setTestDateOverride(LocalDate date){testDateOverride=date;updatedAt=Instant.now();}
}
