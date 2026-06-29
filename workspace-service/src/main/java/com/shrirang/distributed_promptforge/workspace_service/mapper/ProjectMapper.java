package com.shrirang.distributed_promptforge.workspace_service.mapper;

import com.mayur.distributed_promptforge.common_lib.enums.ProjectRole;
import com.shrirang.distributed_promptforge.workspace_service.dto.project.ProjectResponse;
import com.shrirang.distributed_promptforge.workspace_service.dto.project.ProjectSummaryResponse;
import com.shrirang.distributed_promptforge.workspace_service.entity.Project;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ProjectMapper {

    public ProjectResponse toProjectResponse(Project project) {
        if (project == null) {
            return null;
        }

        return new ProjectResponse(
                project.getId(),
                project.getName(),
                project.getCreatedAt(),
                project.getUpdatedAt()
        );
    }

    public ProjectSummaryResponse toProjectSummaryResponse(Project project, ProjectRole role) {
        if (project == null && role == null) {
            return null;
        }

        return new ProjectSummaryResponse(
                project != null ? project.getId() : null,
                project != null ? project.getName() : null,
                project != null ? project.getCreatedAt() : null,
                project != null ? project.getUpdatedAt() : null,
                role
        );
    }

    public List<ProjectSummaryResponse> toListOfProjectSummaryResponse(List<Project> projects) {
        if (projects == null) {
            return null;
        }

        return projects.stream()
                .map(project -> toProjectSummaryResponse(project, null))
                .toList();
    }

}
