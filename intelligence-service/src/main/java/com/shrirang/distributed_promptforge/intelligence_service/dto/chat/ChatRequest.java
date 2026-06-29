package com.shrirang.distributed_promptforge.intelligence_service.dto.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ChatRequest(
        @NotBlank String message,
        @NotNull @Positive Long projectId
) {}
