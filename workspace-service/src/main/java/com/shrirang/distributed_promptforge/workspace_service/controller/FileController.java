package com.shrirang.distributed_promptforge.workspace_service.controller;

import com.mayur.distributed_promptforge.common_lib.dto.FileTreeDto;
import com.mayur.distributed_promptforge.common_lib.error.BadRequestException;
import com.shrirang.distributed_promptforge.workspace_service.dto.project.FileContentResponse;
import com.shrirang.distributed_promptforge.workspace_service.service.ProjectFileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/projects/{projectId}/files")
public class FileController {

    private final ProjectFileService projectFileService;

    @GetMapping
    @PreAuthorize("@security.canViewProject(#p0)")
    public ResponseEntity<FileTreeDto> getFileTree(@PathVariable Long projectId) {
        return ResponseEntity.ok(projectFileService.getFileTree(projectId));
    }

    @GetMapping("/content")
    @PreAuthorize("@security.canViewProject(#p0)")
    public ResponseEntity<FileContentResponse> getFile(
            @PathVariable Long projectId,
            @RequestParam String path) {
        return ResponseEntity.ok(projectFileService.getFileContent(projectId, path));
    }

    @PostMapping("/content")
    @PreAuthorize("@security.canEditProject(#p0)")
    public ResponseEntity<Void> saveFile(
            @PathVariable Long projectId,
            @RequestBody Map<String, String> payload) {
        String path = payload != null ? payload.get("path") : null;
        String content = payload != null ? payload.get("content") : null;
        if (path == null || path.isBlank()) {
            throw new BadRequestException("path is required");
        }
        projectFileService.saveFile(projectId, path, content != null ? content : "");
        return ResponseEntity.ok().build();
    }

    @GetMapping("/download-zip")
    @PreAuthorize("@security.canViewProject(#p0)")
    public ResponseEntity<byte[]> downloadProjectZip(@PathVariable Long projectId) {
        byte[] zipBytes = projectFileService.downloadProjectZip(projectId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=project-" + projectId + ".zip")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(zipBytes);
    }

}
