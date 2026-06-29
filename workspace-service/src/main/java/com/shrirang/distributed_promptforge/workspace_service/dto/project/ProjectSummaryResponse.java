package com.shrirang.distributed_promptforge.workspace_service.dto.project;


import com.mayur.distributed_promptforge.common_lib.enums.ProjectRole;

import java.time.Instant;

public record ProjectSummaryResponse(
        Long id,
        String name,
        Instant createdAt,
        Instant updatedAt,
        ProjectRole role
) {
}
