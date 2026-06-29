package com.shrirang.distributed_promptforge.workspace_service.controller;

import com.mayur.distributed_promptforge.common_lib.dto.FileTreeDto;
import com.mayur.distributed_promptforge.common_lib.enums.ProjectPermission;
import com.mayur.distributed_promptforge.common_lib.error.BadRequestException;
import com.shrirang.distributed_promptforge.workspace_service.service.ProjectFileService;
import com.shrirang.distributed_promptforge.workspace_service.service.ProjectService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URLConnection;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@RequiredArgsConstructor
@RequestMapping("/internal/v1")
@RestController
public class InternalWorkspaceController {

    private final ProjectService projectService;
    private final ProjectFileService projectFileService;

    @GetMapping("/projects/{projectId}/files/tree")
    public FileTreeDto getFileTree(@PathVariable Long projectId) {
        return projectFileService.getFileTree(projectId);
    }

    @GetMapping("/projects/{projectId}/files/content")
    public String getFileContent(@PathVariable Long projectId, @RequestParam String path) {
        return projectFileService.getFileContent(projectId, path).content();
    }

    @PostMapping("/projects/{projectId}/files")
    public ResponseEntity<Void> saveFileContent(
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

    @GetMapping("/projects/{projectId}/permissions/check")
    public boolean checkProjectPermission(
            @PathVariable Long projectId,
            @RequestParam ProjectPermission permission,
            @RequestParam(required = false) Long userId) {
        if (userId != null) {
            return projectService.hasPermission(projectId, userId, permission);
        }
        return projectService.hasPermission(projectId, permission);
    }

    @GetMapping(value = {"/previews/{projectId}", "/previews/{projectId}/**"})
    public ResponseEntity<String> servePreviewFile(
            @PathVariable Long projectId,
            HttpServletRequest request) {

        String prefix = "/internal/v1/previews/" + projectId + "/";
        String uri = request.getRequestURI();
        String relativePath;
        if (uri.startsWith(prefix)) {
            relativePath = uri.substring(prefix.length());
        } else {
            relativePath = "";
        }
        if (relativePath.isBlank()) {
            relativePath = "index.html";
        }
        relativePath = URLDecoder.decode(relativePath, StandardCharsets.UTF_8);

        try {
            String content = projectFileService.getFileContent(projectId, relativePath).content();
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, resolveContentType(relativePath))
                    .body(content);
        } catch (Exception ex) {
            if (!"index.html".equals(relativePath)) {
                try {
                    String indexContent = projectFileService.getFileContent(projectId, "index.html").content();
                    return ResponseEntity.ok()
                            .contentType(MediaType.TEXT_HTML)
                            .body(indexContent);
                } catch (Exception ignored) {
                    // Fall through to 404 below.
                }
            }
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Preview file not found");
        }
    }

    private String resolveContentType(String path) {
        String type = URLConnection.guessContentTypeFromName(path);
        if (type != null) {
            return type;
        }
        if (path.endsWith(".js")) return "text/javascript";
        if (path.endsWith(".css")) return "text/css";
        if (path.endsWith(".json")) return "application/json";
        if (path.endsWith(".svg")) return "image/svg+xml";
        return MediaType.TEXT_PLAIN_VALUE;
    }
}
