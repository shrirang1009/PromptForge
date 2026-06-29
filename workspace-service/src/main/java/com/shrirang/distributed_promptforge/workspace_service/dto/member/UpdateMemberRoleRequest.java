package com.shrirang.distributed_promptforge.workspace_service.dto.member;

import com.mayur.distributed_promptforge.common_lib.enums.ProjectRole;
import jakarta.validation.constraints.NotNull;

public record UpdateMemberRoleRequest(
        @NotNull ProjectRole role) {
}
