package com.shrirang.distributed_promptforge.workspace_service.dto.member;


import com.mayur.distributed_promptforge.common_lib.enums.ProjectRole;

import java.time.Instant;

public record MemberResponse(
        Long userId,
        String username,
        String name,
        ProjectRole role,
        Instant invitedAt
) {
}
