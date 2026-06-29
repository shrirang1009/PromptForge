package com.shrirang.distributed_promptforge.workspace_service.service.impl;

import com.mayur.distributed_promptforge.common_lib.dto.FileNode;
import com.mayur.distributed_promptforge.common_lib.dto.FileTreeDto;
import com.mayur.distributed_promptforge.common_lib.error.ResourceNotFoundException;
import com.shrirang.distributed_promptforge.workspace_service.dto.project.FileContentResponse;
import com.shrirang.distributed_promptforge.workspace_service.entity.Project;
import com.shrirang.distributed_promptforge.workspace_service.entity.ProjectFile;
import com.shrirang.distributed_promptforge.workspace_service.mapper.ProjectFileMapper;
import com.shrirang.distributed_promptforge.workspace_service.repository.ProjectFileRepository;
import com.shrirang.distributed_promptforge.workspace_service.repository.ProjectRepository;
import com.shrirang.distributed_promptforge.workspace_service.service.ProjectFileService;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.ListObjectsArgs;
import io.minio.RemoveObjectsArgs;
import io.minio.Result;
import io.minio.messages.DeleteError;
import io.minio.messages.DeleteObject;
import io.minio.messages.Item;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@Slf4j
@RequiredArgsConstructor
public class ProjectFileServiceImpl implements ProjectFileService {

    private final ProjectRepository projectRepository;
    private final ProjectFileRepository projectFileRepository;
    private final MinioClient minioClient;
    private final ProjectFileMapper projectFileMapper;

    @Value("${minio.project-bucket}")
    private String projectBucket;


    @Override
    @Cacheable(value = "filetree:project", key = "#projectId")
    public FileTreeDto getFileTree(Long projectId) {
        log.debug("File tree cache miss: loading from database: projectId={}", projectId);
        List<ProjectFile> projectFileList = projectFileRepository.findByProjectId(projectId);
        List<FileNode> projectFileNodes = projectFileMapper.toListOfFileNode(projectFileList);
        return new FileTreeDto(projectFileNodes);
    }

    @Override
    public FileContentResponse getFileContent(Long projectId, String path) {
        String objectName = projectId + "/" + path;
        try (
                InputStream is = minioClient.getObject(
                        GetObjectArgs.builder()
                                .bucket(projectBucket)
                                .object(objectName)
                                .build())) {

            String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            return new FileContentResponse(path, content);
        } catch (io.minio.errors.ErrorResponseException e) {
            if ("NoSuchKey".equals(e.errorResponse().code())) {
                log.warn("File not found in MinIO for {}/{}. Returning empty content.", projectId, path);
                return new FileContentResponse(path, "");
            }
            log.error("Failed to read file: {}/{}", projectId, path, e);
            throw new RuntimeException("Failed to read file content", e);
        } catch (Exception e) {
            log.error("Failed to read file: {}/{}", projectId, path, e);
            throw new RuntimeException("Failed to read file content", e);
        }
    }

    @Override
    public byte[] downloadProjectZip(Long projectId) {
        List<ProjectFile> files = projectFileRepository.findByProjectId(projectId);
        try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
             ZipOutputStream zipOutputStream = new ZipOutputStream(byteArrayOutputStream)) {

            for (ProjectFile file : files) {
                String filePath = file.getPath();
                String objectName = projectId + "/" + filePath;
                try (InputStream is = minioClient.getObject(
                        GetObjectArgs.builder()
                                .bucket(projectBucket)
                                .object(objectName)
                                .build())) {
                    zipOutputStream.putNextEntry(new ZipEntry(filePath));
                    zipOutputStream.write(is.readAllBytes());
                    zipOutputStream.closeEntry();
                } catch (io.minio.errors.ErrorResponseException e) {
                    if ("NoSuchKey".equals(e.errorResponse().code())) {
                        log.warn("File not found in MinIO for {}/{}. Skipping in zip.", projectId, filePath);
                        continue;
                    }
                    throw e;
                }
            }
            zipOutputStream.finish();
            return byteArrayOutputStream.toByteArray();
        } catch (Exception e) {
            log.error("Failed to build zip for project: {}", projectId, e);
            throw new RuntimeException("Failed to generate project archive", e);
        }
    }

    @Override
    @Transactional
    @CacheEvict(value = "filetree:project", key = "#projectId")
    public void saveFile(Long projectId, String path, String content) {
        Project project = projectRepository.findById(projectId).orElseThrow(
                () -> new ResourceNotFoundException("Project", projectId.toString())
        );

        String cleanPath = path.startsWith("/") ? path.substring(1) : path;
        String objectKey = projectId + "/" + cleanPath;

        try {
            byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);
            InputStream inputStream = new ByteArrayInputStream(contentBytes);
            // saving the file content
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(projectBucket)
                            .object(objectKey)
                            .stream(inputStream, contentBytes.length, -1)
                            .contentType(determineContentType(path))
                            .build());

            // Saving the metaData
            ProjectFile file = projectFileRepository.findByProjectIdAndPath(projectId, cleanPath)
                    .orElseGet(() -> ProjectFile.builder()
                            .project(project)
                            .path(cleanPath)
                            .minioObjectKey(objectKey) // Use the key we generated
                            .createdAt(Instant.now())
                            .build());

            file.setUpdatedAt(Instant.now());
            projectFileRepository.save(file);

            log.info("Saved file and evicted file tree cache: {}", objectKey);
        } catch (Exception e) {
            log.error("Failed to save file {}/{}", projectId, cleanPath, e);
            throw new RuntimeException("File save failed", e);
        }

    }

    private String determineContentType(String path) {
        String type = URLConnection.guessContentTypeFromName(path);
        if (type != null) return type;
        if (path.endsWith(".html") || path.endsWith(".htm")) return "text/html";
        if (path.endsWith(".js") || path.endsWith(".mjs")) return "text/javascript";
        if (path.endsWith(".jsx") || path.endsWith(".ts") || path.endsWith(".tsx")) return "text/javascript";
        if (path.endsWith(".json")) return "application/json";
        if (path.endsWith(".css")) return "text/css";

        return "text/plain";
    }

    @Override
    public void deleteProjectFolder(Long projectId) {
        String prefix = projectId + "/";
        log.info("Deleting all physical files in MinIO for project: {}", projectId);
        try {
            Iterable<Result<Item>> results = minioClient.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(projectBucket)
                            .prefix(prefix)
                            .recursive(true)
                            .build()
            );

            List<DeleteObject> objectsToDelete = new java.util.ArrayList<>();
            for (Result<Item> result : results) {
                Item item = result.get();
                objectsToDelete.add(new DeleteObject(item.objectName()));
            }

            if (!objectsToDelete.isEmpty()) {
                Iterable<Result<DeleteError>> deleteResults = minioClient.removeObjects(
                        RemoveObjectsArgs.builder()
                                .bucket(projectBucket)
                                .objects(objectsToDelete)
                                .build()
                );
                // Iterate over the results to trigger lazy evaluation of object removal
                for (Result<DeleteError> result : deleteResults) {
                    DeleteError error = result.get();
                    log.error("Failed to delete object from MinIO: {}", error.objectName());
                }
            }
            log.info("Successfully deleted MinIO folder for project {}", projectId);
        } catch (Exception e) {
            log.error("Failed to delete MinIO folder for project {}", projectId, e);
            throw new RuntimeException("MinIO folder deletion failed", e);
        }
    }
}
