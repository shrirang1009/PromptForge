package com.shrirang.distributed_promptforge.workspace_service.service;

import com.mayur.distributed_promptforge.common_lib.enums.ProjectPermission;
import com.shrirang.distributed_promptforge.workspace_service.dto.project.ProjectRequest;
import com.shrirang.distributed_promptforge.workspace_service.dto.project.ProjectSummaryResponse;

import java.util.List;

public interface ProjectService {
    List<ProjectSummaryResponse> getUserProjects();

    ProjectSummaryResponse getUserProjectById(Long projectId);

    /** Returns the newly created project with role=OWNER already set. */
    ProjectSummaryResponse createProject(ProjectRequest request);

    ProjectSummaryResponse updateProject(Long projectId, ProjectRequest request);

    void deleteProject(Long projectId);

    boolean hasPermission(Long projectId, ProjectPermission permission);

    boolean hasPermission(Long projectId, Long userId, ProjectPermission permission);
}
