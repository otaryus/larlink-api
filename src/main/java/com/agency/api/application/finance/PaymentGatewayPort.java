package com.agency.api.application.finance;

import java.math.BigDecimal;
import java.util.UUID;

public interface PaymentGatewayPort {
    record Invoice(UUID obligationId, BigDecimal amount, String clientName, String description) {}
    record IssuedReference(String provider, String entity, String reference) {}
    record Callback(UUID obligationId, String transactionId, BigDecimal amount) {}

    boolean isConfigured();
    IssuedReference issue(Invoice invoice);
    boolean verifyCallback(String rawBody, String signature);
    Callback parseCallback(String rawBody) throws Exception;
}
