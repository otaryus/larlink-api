package com.agency.api.service;

import org.springframework.stereotype.Service;

@Service
public class SalaryNegotiationPolicy {

    public static final int MINIMUM_SALARY = 5_000;
    public static final int NEGOTIATION_INCREMENT = 50;

    public void validateOffer(int monthlySalary) {
        if (monthlySalary < MINIMUM_SALARY) {
            throw new IllegalArgumentException("O salário proposto deve ser igual ou superior a 5.000 MT.");
        }
        if (monthlySalary % NEGOTIATION_INCREMENT != 0) {
            throw new IllegalArgumentException("O salário proposto deve ser um múltiplo de 50 MT.");
        }
    }
}
