package com.shrirang.distributed_promptforge.workspace_service.mapper;

import com.shrirang.distributed_promptforge.workspace_service.dto.member.MemberResponse;
import com.shrirang.distributed_promptforge.workspace_service.entity.ProjectMember;
import org.springframework.stereotype.Component;

@Component
public class ProjectMemberMapper {

    public MemberResponse toProjectMemberResponseFromMember(ProjectMember projectMember) {
        if (projectMember == null) {
            return null;
        }

        return new MemberResponse(
                projectMember.getId() != null ? projectMember.getId().getUserId() : null,
                null,
                null,
                projectMember.getProjectRole(),
                projectMember.getInvitedAt()
        );
    }
}
