package com.shrirang.distributed_promptforge.account_service.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record VerifyOtpRequest(
        @Email @NotBlank String email,
        @NotBlank String code
) {
}
