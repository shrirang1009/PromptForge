package com.shrirang.distributed_promptforge.intelligence_service.client.fallback;

import com.mayur.distributed_promptforge.common_lib.dto.FileTreeDto;
import com.mayur.distributed_promptforge.common_lib.enums.ProjectPermission;
import com.shrirang.distributed_promptforge.intelligence_service.client.WorkspaceClient;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class WorkspaceClientFallback implements WorkspaceClient {

    @Override
    public FileTreeDto getFileTree(Long projectId) {
        log.warn("WorkspaceClientFallback: Workspace Service is down. getFileTree failed for projectId={}", projectId);
        return new FileTreeDto(List.of());
    }

    @Override
    public String getFileContent(Long projectId, String path) {
        log.warn("WorkspaceClientFallback: Workspace Service is down. getFileContent failed for projectId={}, path={}", projectId, path);
        return "Workspace service unavailable";
    }

    @Override
    public boolean checkPermission(Long projectId, ProjectPermission permission, Long userId) {
        log.warn("WorkspaceClientFallback: Workspace Service is down. checkPermission failed for projectId={}, permission={}, userId={}", projectId, permission, userId);
        return false;
    }

    @Override
    public void saveFile(Long projectId, Map<String, String> payload) {
        log.error("WorkspaceClientFallback: Workspace Service is down. saveFile failed for projectId={}", projectId);
        throw new IllegalStateException("Workspace service currently unavailable");
    }
}
