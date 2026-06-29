package com.shrirang.distributed_promptforge.intelligence_service.security;

import com.mayur.distributed_promptforge.common_lib.enums.ProjectPermission;
import com.mayur.distributed_promptforge.common_lib.security.AuthUtil;
import com.shrirang.distributed_promptforge.intelligence_service.client.WorkspaceClient;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.stereotype.Component;

@Component("security")
@RequiredArgsConstructor
@Slf4j
public class SecurityExpressions {

    private final AuthUtil authUtil;
    private final WorkspaceClient workspaceClient;

    private boolean hasPermission(Long projectId, ProjectPermission projectPermission) {
        Long userId = authUtil.getCurrentUserId();
        try {
            boolean allowed = workspaceClient.checkPermission(projectId, projectPermission, userId);
            if (!allowed) {
                log.warn("Permission denied by workspace-service: userId={}, projectId={}, permission={}",
                        userId, projectId, projectPermission);
            }
            return allowed;
        } catch (FeignException.Unauthorized e) {
            log.warn("Token expired or invalid during permission check: userId={}, projectId={}, permission={}",
                    userId, projectId, projectPermission);
            throw new CredentialsExpiredException("JWT token is expired or invalid");
        } catch (FeignException.Forbidden e) {
            log.warn("Workspace-service returned 403 for permission check: userId={}, projectId={}, permission={}",
                    userId, projectId, projectPermission);
            return false;
        } catch (FeignException e) {
            log.error("Workspace-service failed during permission check: userId={}, projectId={}, permission={}, status={}, message={}",
                    userId, projectId, projectPermission, e.status(), e.getMessage());
            throw new IllegalStateException("Permission check temporarily unavailable");
        }
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
