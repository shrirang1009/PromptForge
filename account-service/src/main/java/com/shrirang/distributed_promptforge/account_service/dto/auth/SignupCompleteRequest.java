package com.shrirang.distributed_promptforge.account_service.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SignupCompleteRequest(
        @Email @NotBlank String email,
        @NotBlank @Size(min = 1, max = 50) String name,
        @NotBlank @Size(min = 4) String password
) {
}
