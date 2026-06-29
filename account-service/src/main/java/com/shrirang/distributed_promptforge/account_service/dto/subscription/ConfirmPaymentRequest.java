package com.shrirang.distributed_promptforge.account_service.dto.subscription;

import jakarta.validation.constraints.NotBlank;

public record ConfirmPaymentRequest(
        @NotBlank String orderId,
        @NotBlank String paymentId
) {
}
