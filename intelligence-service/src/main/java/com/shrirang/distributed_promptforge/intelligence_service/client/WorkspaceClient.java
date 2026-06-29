package com.shrirang.distributed_promptforge.intelligence_service.client;

import com.mayur.distributed_promptforge.common_lib.dto.FileTreeDto;
import com.mayur.distributed_promptforge.common_lib.enums.ProjectPermission;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import com.shrirang.distributed_promptforge.intelligence_service.client.fallback.WorkspaceClientFallback;
import java.util.Map;

@FeignClient(name = "workspace-service", fallback = WorkspaceClientFallback.class)
public interface WorkspaceClient {

    @GetMapping("/internal/v1/projects/{projectId}/files/tree")
    FileTreeDto getFileTree(@PathVariable("projectId") Long projectId);

    @GetMapping("/internal/v1/projects/{projectId}/files/content")
    String getFileContent(@PathVariable("projectId") Long projectId, @RequestParam("path") String path);

    @GetMapping("/internal/v1/projects/{projectId}/permissions/check")
    boolean checkPermission(
            @PathVariable("projectId") Long projectId,
            @RequestParam("permission") ProjectPermission permission,
            @RequestParam("userId") Long userId);

    @PostMapping("/internal/v1/projects/{projectId}/files")
    void saveFile(
            @PathVariable("projectId") Long projectId,
            @RequestBody Map<String, String> payload);
}
