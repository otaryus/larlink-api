package com.agency.api.service;

import java.math.BigDecimal;
import java.util.UUID;

/** Adapter contract for an authorized Mozambican payment provider. Implement with provider-issued references and signed callbacks. */
public interface PaymentProvider {
    record Invoice(UUID obligationId,BigDecimal amount,String clientName,String description){}
    record IssuedReference(String provider,String entity,String reference,String providerTransactionId){}
    boolean isConfigured();
    IssuedReference issue(Invoice invoice);
    boolean verifyCallback(String rawBody,String signature);
}
