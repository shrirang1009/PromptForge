package com.shrirang.distributed_promptforge.account_service.dto.subscription;

public record UsageTodayResponse(
        Integer tokensUsed,
        Integer tokensLimit,
        Integer tokensRemaining,
        Integer tokensAllowed,
        Integer previewsRunning,
        Integer previewsLimit
) {
}
