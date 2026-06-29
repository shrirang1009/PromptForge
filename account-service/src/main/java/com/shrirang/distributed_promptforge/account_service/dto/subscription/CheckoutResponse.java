package com.shrirang.distributed_promptforge.account_service.dto.subscription;

public record CheckoutResponse(
        String checkoutUrl,
        String orderId,
        String keyId,
        Long amount,
        String currency,
        String customerName,
        String customerEmail
) {
}
