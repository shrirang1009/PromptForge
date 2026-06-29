package com.shrirang.distributed_promptforge.workspace_service.service.impl;

import com.mayur.distributed_promptforge.common_lib.dto.UserDto;
import com.mayur.distributed_promptforge.common_lib.error.BadRequestException;
import com.mayur.distributed_promptforge.common_lib.error.ResourceNotFoundException;
import com.mayur.distributed_promptforge.common_lib.enums.ProjectRole;
import com.mayur.distributed_promptforge.common_lib.security.AuthUtil;
import com.shrirang.distributed_promptforge.workspace_service.client.AccountClient;
import com.shrirang.distributed_promptforge.workspace_service.dto.member.InviteMemberRequest;
import com.shrirang.distributed_promptforge.workspace_service.dto.member.MemberResponse;
import com.shrirang.distributed_promptforge.workspace_service.dto.member.UpdateMemberRoleRequest;
import com.shrirang.distributed_promptforge.workspace_service.entity.Project;
import com.shrirang.distributed_promptforge.workspace_service.entity.ProjectMember;
import com.shrirang.distributed_promptforge.workspace_service.entity.ProjectMemberId;
import com.shrirang.distributed_promptforge.workspace_service.mapper.ProjectMemberMapper;
import com.shrirang.distributed_promptforge.workspace_service.repository.ProjectMemberRepository;
import com.shrirang.distributed_promptforge.workspace_service.repository.ProjectRepository;
import com.shrirang.distributed_promptforge.workspace_service.service.ProjectMemberService;
import jakarta.transaction.Transactional;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@RequiredArgsConstructor
@Transactional
public class ProjectMemberServiceImpl implements ProjectMemberService {

    ProjectMemberRepository projectMemberRepository;
    ProjectRepository projectRepository;
    ProjectMemberMapper projectMemberMapper;
    AuthUtil authUtil;
    AccountClient accountClient;

    @Override
    @PreAuthorize("@security.canViewMembers(#p0)")
    public List<MemberResponse> getProjectMembers(Long projectId) {
        return projectMemberRepository.findByIdProjectId(projectId)
                .stream()
                .map(this::toMemberResponseWithUserInfo)
                .toList();
    }

    @Override
    @PreAuthorize("@security.canManageMembers(#p0)")
    public MemberResponse inviteMember(Long projectId, InviteMemberRequest request) {
        Long userId = authUtil.getCurrentUserId();
        Project project = getAccessibleProjectById(projectId, userId);

        if (request.role() == ProjectRole.OWNER) {
            throw new BadRequestException("Owner role can only be assigned when the project is created");
        }

        UserDto invitee = accountClient.getUserByEmail(request.username()).orElseThrow(
                () -> new ResourceNotFoundException("User", request.username())
        );

        if(invitee.id().equals(userId)) {
            throw new BadRequestException("Cannot invite yourself");
        }

        ProjectMemberId projectMemberId = new ProjectMemberId(projectId, invitee.id());

        if(projectMemberRepository.existsById(projectMemberId)) {
            throw new BadRequestException("Member already exists in project");
        }

        ProjectMember member = ProjectMember.builder()
                .id(projectMemberId)
                .project(project)
                .projectRole(request.role())
                .invitedAt(Instant.now())
                .build();

        projectMemberRepository.save(member);

        return toMemberResponseWithUserInfo(member);
    }

    @Override
    @PreAuthorize("@security.canManageMembers(#p0)")
    public MemberResponse updateMemberRole(Long projectId, Long memberId, UpdateMemberRoleRequest request) {
        Long userId = authUtil.getCurrentUserId();
        getAccessibleProjectById(projectId, userId);

        if (request.role() == ProjectRole.OWNER) {
            throw new BadRequestException("Owner role cannot be assigned through sharing");
        }

        ProjectMemberId projectMemberId = new ProjectMemberId(projectId, memberId);
        ProjectMember projectMember = projectMemberRepository.findById(projectMemberId)
                .orElseThrow(() -> new ResourceNotFoundException("ProjectMember", memberId.toString()));

        if (projectMember.getProjectRole() == ProjectRole.OWNER) {
            throw new BadRequestException("Project owner access cannot be changed");
        }

        projectMember.setProjectRole(request.role());

        projectMemberRepository.save(projectMember);

        return toMemberResponseWithUserInfo(projectMember);
    }

    @Override
    @PreAuthorize("@security.canManageMembers(#p0)")
    public void removeProjectMember(Long projectId, Long memberId) {
        Long userId = authUtil.getCurrentUserId();
        getAccessibleProjectById(projectId, userId);

        ProjectMemberId projectMemberId = new ProjectMemberId(projectId, memberId);
        ProjectMember projectMember = projectMemberRepository.findById(projectMemberId)
                .orElseThrow(() -> new ResourceNotFoundException("ProjectMember", memberId.toString()));

        if (projectMember.getProjectRole() == ProjectRole.OWNER) {
            throw new BadRequestException("Project owner cannot be removed");
        }


        projectMemberRepository.deleteById(projectMemberId);
    }

    ///  INTERNAL FUNCTIONS

    public Project getAccessibleProjectById(Long projectId, Long userId) {
        return projectRepository.findAccessibleProjectById(projectId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", projectId.toString()));
    }

    private MemberResponse toMemberResponseWithUserInfo(ProjectMember member) {
        MemberResponse mapped = projectMemberMapper.toProjectMemberResponseFromMember(member);
        UserDto user = accountClient.getUserById(member.getId().getUserId());
        return new MemberResponse(
                mapped.userId(),
                user.username(),
                user.name(),
                mapped.role(),
                mapped.invitedAt()
        );
    }
}
