package com.shrirang.distributed_promptforge.workspace_service.security;

import com.mayur.distributed_promptforge.common_lib.enums.ProjectPermission;
import com.mayur.distributed_promptforge.common_lib.security.AuthUtil;
import com.shrirang.distributed_promptforge.workspace_service.repository.ProjectMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component("security")
@RequiredArgsConstructor
public class SecurityExpressions {

    private final ProjectMemberRepository projectMemberRepository;
    private final AuthUtil authUtil;

    public boolean hasPermission(Long projectId, ProjectPermission projectPermission) {
        Long userId = authUtil.getCurrentUserId();
        return hasPermission(projectId, userId, projectPermission);
    }

    public boolean hasPermission(Long projectId, Long userId, ProjectPermission projectPermission) {
        System.out.println("=== SECURITY PERMISSION CHECK ===");
        System.out.println("projectId: " + projectId);
        System.out.println("userId: " + userId);
        System.out.println("permission: " + projectPermission);
        boolean result = projectMemberRepository.findRoleByProjectIdAndUserId(projectId, userId).
                map(role -> {
                    System.out.println("Found user role: " + role);
                    System.out.println("Permissions: " + role.getPermissions());
                    return role.getPermissions().contains(projectPermission);
                })
                .orElse(false);
        System.out.println("result: " + result);
        System.out.println("=================================");
        return result;
    }

    public boolean canViewProject(Long projectId) {
        return hasPermission(projectId, ProjectPermission.VIEW);
    }

    public boolean canEditProject(Long projectId) {
        return hasPermission(projectId, ProjectPermission.EDIT);
    }

    public boolean canDeleteProject(Long projectId) {
        return hasPermission(projectId, ProjectPermission.DELETE);
    }

    public boolean canViewMembers(Long projectId) {
        return hasPermission(projectId, ProjectPermission.VIEW_MEMBERS);
    }

    public boolean canManageMembers(Long projectId) {
        return hasPermission(projectId, ProjectPermission.MANAGE_MEMBERS);
    }
}
