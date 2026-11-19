package com.agency.api.application.finance;

import com.agency.api.domain.PaymentObligation;

public record PaymentObligationResult(PaymentObligation obligation, boolean created) {
}
