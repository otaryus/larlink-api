package com.agency.api.service;

import org.springframework.stereotype.Component;

@Component
public class UnconfiguredPaymentProvider implements PaymentProvider {
    @Override public boolean isConfigured(){return false;}
    @Override public IssuedReference issue(Invoice invoice){throw new IllegalStateException("Nenhum prestador de pagamentos está configurado.");}
    @Override public boolean verifyCallback(String rawBody,String signature){return false;}
}
