package com.shrirang.distributed_promptforge.workspace_service.service.impl;

import com.mayur.distributed_promptforge.common_lib.dto.PlanDto;
import com.mayur.distributed_promptforge.common_lib.enums.ProjectPermission;
import com.mayur.distributed_promptforge.common_lib.enums.ProjectRole;
import com.mayur.distributed_promptforge.common_lib.error.BadRequestException;
import com.mayur.distributed_promptforge.common_lib.error.ResourceNotFoundException;
import com.mayur.distributed_promptforge.common_lib.security.AuthUtil;
import com.shrirang.distributed_promptforge.workspace_service.client.AccountClient;
import com.shrirang.distributed_promptforge.workspace_service.dto.project.ProjectRequest;
import com.shrirang.distributed_promptforge.workspace_service.dto.project.ProjectSummaryResponse;
import com.shrirang.distributed_promptforge.workspace_service.entity.Project;
import com.shrirang.distributed_promptforge.workspace_service.entity.ProjectMember;
import com.shrirang.distributed_promptforge.workspace_service.entity.ProjectMemberId;
import com.shrirang.distributed_promptforge.workspace_service.mapper.ProjectMapper;
import com.shrirang.distributed_promptforge.workspace_service.repository.ProjectFileRepository;
import com.shrirang.distributed_promptforge.workspace_service.repository.ProjectMemberRepository;
import com.shrirang.distributed_promptforge.workspace_service.repository.ProjectRepository;
import org.springframework.cache.annotation.CacheEvict;
import com.shrirang.distributed_promptforge.workspace_service.security.SecurityExpressions;
import com.shrirang.distributed_promptforge.workspace_service.service.ProjectFileService;
import com.shrirang.distributed_promptforge.workspace_service.service.ProjectService;
import com.shrirang.distributed_promptforge.workspace_service.service.ProjectTemplateService;
import jakarta.transaction.Transactional;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@Transactional
public class ProjectServiceImpl implements ProjectService {

    ProjectRepository projectRepository;
    ProjectMapper projectMapper;
    ProjectMemberRepository projectMemberRepository;
    AuthUtil authUtil;
    ProjectTemplateService projectTemplateService;
    AccountClient accountClient;
    SecurityExpressions securityExpressions;
    ProjectFileRepository projectFileRepository;
    ProjectFileService projectFileService;

    @Override
    public ProjectSummaryResponse createProject(ProjectRequest request) {
        if (!canCreateProject()) {
            throw new BadRequestException(
                    "Cannot create a new project with your current plan. Please upgrade.");
        }

        Long ownerUserId = authUtil.getCurrentUserId();

        Project project = Project.builder()
                .name(request.name())
                .isPublic(false)
                .build();
        project = projectRepository.save(project);

        ProjectMemberId projectMemberId = new ProjectMemberId(project.getId(), ownerUserId);
        ProjectMember projectMember = ProjectMember.builder()
                .id(projectMemberId)
                .projectRole(ProjectRole.OWNER)
                .acceptedAt(Instant.now())
                .invitedAt(Instant.now())
                .project(project)
                .build();
        projectMemberRepository.save(projectMember);

        projectTemplateService.initializeProjectFromTemplate(project.getId());

        return projectMapper.toProjectSummaryResponse(project, ProjectRole.OWNER);
    }

    @Override
    public List<ProjectSummaryResponse> getUserProjects() {
        Long userId = authUtil.getCurrentUserId();
        var projectsWithRoles = projectRepository.findAllAccessibleByUser(userId);
        return projectsWithRoles.stream()
                .map(p -> projectMapper.toProjectSummaryResponse(p.getProject(), p.getRole()))
                .toList();
    }

    @Override
    @PreAuthorize("@security.canViewProject(#p0)")
    public ProjectSummaryResponse getUserProjectById(Long projectId) {
        Long userId = authUtil.getCurrentUserId();
        var projectWithRole = projectRepository.findAccessibleProjectByIdWithRole(projectId, userId)
                .orElseThrow(() -> new BadRequestException("Project not found"));
        return projectMapper.toProjectSummaryResponse(
                projectWithRole.getProject(), projectWithRole.getRole());
    }

    @Override
    @PreAuthorize("@security.canEditProject(#p0)")
    public ProjectSummaryResponse updateProject(Long projectId, ProjectRequest request) {
        Long userId = authUtil.getCurrentUserId();
        Project project = getAccessibleProjectById(projectId, userId);
        project.setName(request.name());
        project = projectRepository.save(project);

        var projectWithRole = projectRepository.findAccessibleProjectByIdWithRole(projectId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", projectId.toString()));
        return projectMapper.toProjectSummaryResponse(project, projectWithRole.getRole());
    }

    @Override
    @PreAuthorize("@security.canDeleteProject(#p0)")
    @CacheEvict(value = "filetree:project", key = "#projectId")
    public void deleteProject(Long projectId) {
        Long userId = authUtil.getCurrentUserId();
        Project project = getAccessibleProjectById(projectId, userId);

        // Pehle saare project files database metadata delete karo
        projectFileRepository.deleteAll(projectFileRepository.findByProjectId(projectId));

        // MinIO physical folder delete karo
        projectFileService.deleteProjectFolder(projectId);

        // Phir saare project members delete karo
        projectMemberRepository.deleteAll(projectMemberRepository.findByIdProjectId(projectId));

        // Aakhir mein project khud permanently delete karo
        projectRepository.delete(project);
    }

    @Override
    public boolean hasPermission(Long projectId, ProjectPermission permission) {
        return securityExpressions.hasPermission(projectId, permission);
    }

    @Override
    public boolean hasPermission(Long projectId, Long userId, ProjectPermission permission) {
        return securityExpressions.hasPermission(projectId, userId, permission);
    }

    // ─── Internal helpers ─────────────────────────────────────────────────────

    public Project getAccessibleProjectById(Long projectId, Long userId) {
        return projectRepository.findAccessibleProjectById(projectId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", projectId.toString()));
    }

    private boolean canCreateProject() {
        Long userId = authUtil.getCurrentUserId();
        if (userId == null) return false;

        PlanDto plan = accountClient.getCurrentSubscribedPlanByUserId(userId);
        int maxAllowed = (plan != null && plan.maxProjects() != null) ? plan.maxProjects() : 5;
        int ownedCount = projectMemberRepository.countProjectOwnedByUser(userId);
        return ownedCount < maxAllowed;
    }
}
