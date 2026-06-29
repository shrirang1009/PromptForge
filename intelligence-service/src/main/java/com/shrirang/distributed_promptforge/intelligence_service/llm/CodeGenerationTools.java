package com.shrirang.distributed_promptforge.intelligence_service.llm;

import com.shrirang.distributed_promptforge.intelligence_service.client.WorkspaceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
@Slf4j
public class CodeGenerationTools {

    private static final int MAX_FILES_PER_TOOL_CALL = 8;
    private static final int MAX_FILE_CONTENT_CHARS = 250_000;

    private final Long projectId;
    private final WorkspaceClient workspaceClient;

    public record ReadFilesRequest(
            @ToolParam(description = "Relative file paths present in FILE_TREE. Examples: [\"index.html\",\"style.css\",\"script.js\"]")
            List<String> paths
    ) {
    }

    public record WriteFileRequest(
            @ToolParam(description = "Relative path to write. Examples: index.html, style.css, script.js")
            String path,

            @ToolParam(description = "Complete file content. Do not use placeholders.")
            String content
    ) {
    }

    public record WriteFilesRequest(
            @ToolParam(description = "Files to write. Maximum 8 files per call.")
            List<WriteFileRequest> files
    ) {
    }

    @Tool(name = "read_files",
            description = "Read files from the current project. Input JSON must be an object: {\"paths\":[\"index.html\",\"style.css\",\"script.js\"]}.")
    public List<String> readFiles(
            @ToolParam(description = "Object containing relative file paths to read")
            ReadFilesRequest request
    ) {
        List<String> paths = validateReadFilesRequest(request);
        List<String> result = new ArrayList<>();

        for (String path : paths) {
            String cleanPath = normalizePath(path);
            log.info("Tool read_files: projectId={}, path={}", projectId, cleanPath);

            String content = workspaceClient.getFileContent(projectId, cleanPath);
            result.add(String.format(
                    "--- START OF FILE: %s ---%n%s%n--- END OF FILE ---",
                    cleanPath, content
            ));
        }

        return result;
    }

    @Tool(name = "write_files",
            description = "Write one or more complete files. Input JSON must be: {\"files\":[{\"path\":\"index.html\",\"content\":\"...\"},{\"path\":\"style.css\",\"content\":\"...\"},{\"path\":\"script.js\",\"content\":\"...\"}]}.")
    public String writeFiles(
            @ToolParam(description = "Object containing files array with path and complete content")
            WriteFilesRequest request
    ) {
        List<WriteFileRequest> files = validateWriteFilesRequest(request);
        int savedCount = 0;

        for (WriteFileRequest file : files) {
            savedCount += saveValidatedFile(file) ? 1 : 0;
        }

        return "ACK: write_files saved " + savedCount + " files.";
    }

    @Tool(name = "write_file",
            description = "Write a single complete file. Input JSON must be: {\"path\":\"index.html\",\"content\":\"...\"}.")
    public String writeFile(
            @ToolParam(description = "Single file object with path and complete content")
            WriteFileRequest request
    ) {
        WriteFileRequest file = validateWriteFileRequest(request);
        boolean saved = saveValidatedFile(file);
        return saved
                ? "ACK: write_file saved " + normalizePath(file.path()) + "."
                : "ACK: write_file failed to save " + normalizePath(file.path()) + ".";
    }

    private List<String> validateReadFilesRequest(ReadFilesRequest request) {
        if (request == null || request.paths() == null || request.paths().isEmpty()) {
            throw new IllegalArgumentException("read_files requires at least one path");
        }
        if (request.paths().size() > MAX_FILES_PER_TOOL_CALL) {
            throw new IllegalArgumentException("read_files supports at most " + MAX_FILES_PER_TOOL_CALL + " paths");
        }
        request.paths().forEach(this::validatePath);
        log.debug("Tool read_files args: projectId={}, paths={}", projectId, request.paths());
        return request.paths();
    }

    private List<WriteFileRequest> validateWriteFilesRequest(WriteFilesRequest request) {
        if (request == null || request.files() == null || request.files().isEmpty()) {
            throw new IllegalArgumentException("write_files requires at least one file");
        }
        if (request.files().size() > MAX_FILES_PER_TOOL_CALL) {
            throw new IllegalArgumentException("write_files supports at most " + MAX_FILES_PER_TOOL_CALL + " files");
        }
        request.files().forEach(this::validateWriteFileRequest);
        log.debug("Tool write_files args: projectId={}, fileCount={}", projectId, request.files().size());
        return request.files();
    }

    private WriteFileRequest validateWriteFileRequest(WriteFileRequest file) {
        if (file == null) {
            throw new IllegalArgumentException("file payload is required");
        }
        validatePath(file.path());
        if (file.content() == null) {
            throw new IllegalArgumentException("file content is required");
        }
        if (file.content().length() > MAX_FILE_CONTENT_CHARS) {
            throw new IllegalArgumentException("file content is too large: " + normalizePath(file.path()));
        }
        return file;
    }

    private boolean saveValidatedFile(WriteFileRequest file) {
        String cleanPath = normalizePath(file.path());
        String content = normalizeFileContent(file.content());

        log.info("Tool write file: projectId={}, path={}, contentLength={}",
                projectId, cleanPath, content.length());
        log.debug("Tool write file args: projectId={}, path={}, contentPreview={}",
                projectId, cleanPath, preview(content));

        try {
            workspaceClient.saveFile(projectId, Map.of("path", cleanPath, "content", content));
            return true;
        } catch (Exception ex) {
            log.error("Failed to save file via tool: projectId={}, path={}", projectId, cleanPath, ex);
            return false;
        }
    }

    private void validatePath(String path) {
        String cleanPath = normalizePath(path);
        if (cleanPath.isBlank()) {
            throw new IllegalArgumentException("path is required");
        }
        if (cleanPath.contains("..") || cleanPath.startsWith("~") || cleanPath.contains("\\") || cleanPath.contains(":")) {
            throw new IllegalArgumentException("path must be a safe project-relative path: " + path);
        }
    }

    private String normalizePath(String path) {
        if (path == null) {
            return "";
        }
        String normalized = path.trim();
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    private String preview(String content) {
        String flattened = content.replace("\r", "\\r").replace("\n", "\\n");
        return flattened.length() <= 500 ? flattened : flattened.substring(0, 500) + "...";
    }

    private String normalizeFileContent(String content) {
        String normalized = content.replace("\u0000", "");
        String trimmed = normalized.trim();
        if (trimmed.startsWith("```") && trimmed.endsWith("```")) {
            int firstLineEnd = trimmed.indexOf('\n');
            if (firstLineEnd > 0) {
                String withoutOpeningFence = trimmed.substring(firstLineEnd + 1);
                int closingFence = withoutOpeningFence.lastIndexOf("```");
                if (closingFence >= 0) {
                    return withoutOpeningFence.substring(0, closingFence).stripTrailing();
                }
            }
        }
        return normalized;
    }
}
