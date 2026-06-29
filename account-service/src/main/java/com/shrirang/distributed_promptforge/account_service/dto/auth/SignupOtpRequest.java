package com.shrirang.distributed_promptforge.account_service.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record SignupOtpRequest(
        @Email @NotBlank String email
) {
}
