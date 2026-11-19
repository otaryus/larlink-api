package com.agency.api.adapters.payments;

import com.agency.api.application.finance.PaymentGatewayPort;
import com.agency.api.service.PaymentProvider;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.UUID;

@Component
public class PaymentGatewayAdapter implements PaymentGatewayPort {
    private final PaymentProvider provider;
    private final ObjectMapper mapper;

    public PaymentGatewayAdapter(PaymentProvider provider, ObjectMapper mapper) {
        this.provider = provider;
        this.mapper = mapper;
    }

    @Override public boolean isConfigured() { return provider.isConfigured(); }

    @Override
    public IssuedReference issue(Invoice invoice) {
        PaymentProvider.IssuedReference result = provider.issue(new PaymentProvider.Invoice(
                invoice.obligationId(), invoice.amount(), invoice.clientName(), invoice.description()));
        return result == null ? null : new IssuedReference(result.provider(), result.entity(), result.reference());
    }

    @Override public boolean verifyCallback(String rawBody, String signature) {
        return provider.verifyCallback(rawBody, signature);
    }

    @Override
    public Callback parseCallback(String rawBody) throws Exception {
        var data = mapper.readTree(rawBody);
        return new Callback(UUID.fromString(data.path("obligationId").asText()),
                data.path("transactionId").asText(), data.path("amount").decimalValue());
    }
}
