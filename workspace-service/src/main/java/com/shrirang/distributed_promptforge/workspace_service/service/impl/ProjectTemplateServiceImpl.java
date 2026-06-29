package com.shrirang.distributed_promptforge.workspace_service.service.impl;

import com.mayur.distributed_promptforge.common_lib.error.ResourceNotFoundException;
import com.shrirang.distributed_promptforge.workspace_service.entity.Project;
import com.shrirang.distributed_promptforge.workspace_service.entity.ProjectFile;
import com.shrirang.distributed_promptforge.workspace_service.repository.ProjectFileRepository;
import com.shrirang.distributed_promptforge.workspace_service.repository.ProjectRepository;
import com.shrirang.distributed_promptforge.workspace_service.service.ProjectTemplateService;
import io.minio.*;
import io.minio.messages.Item;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
@Service
@Slf4j
public class ProjectTemplateServiceImpl implements ProjectTemplateService {

    private final MinioClient minioClient;
    private final ProjectFileRepository projectFileRepository;
    private final ProjectRepository projectRepository;

    private static final String TEMPLATE_BUCKET = "templates";
    private static final String TEMPLATE_NAME = "react-vite-tailwind-daisyui-starter";

    @Value("${minio.project-bucket}")
    private String projectBucket;

    @Override
    @CacheEvict(value = "filetree:project", key = "#projectId")
    public void initializeProjectFromTemplate(Long projectId) {
        Project project = projectRepository.findById(projectId).orElseThrow(
                () -> new ResourceNotFoundException("Project", projectId.toString()));

        try {
            Iterable<Result<Item>> results = minioClient.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(TEMPLATE_BUCKET)
                            .prefix(TEMPLATE_NAME + "/")
                            .recursive(true)
                            .build()
            );

            List<ProjectFile> filesToSave = new ArrayList<>();

            for (Result<Item> result : results) {
                Item item = result.get();
                String sourceKey = item.objectName();

                String cleanPath = sourceKey.replaceFirst(TEMPLATE_NAME + "/", "");
                String destKey = projectId + "/" + cleanPath;

                minioClient.copyObject(
                        CopyObjectArgs.builder()
                                .bucket(projectBucket)
                                .object(destKey)
                                .source(
                                        CopySource.builder()
                                                .bucket(TEMPLATE_BUCKET)
                                                .object(sourceKey)
                                                .build()
                                )
                                .build()
                );

                ProjectFile pf = ProjectFile.builder()
                        .project(project)
                        .path(cleanPath)
                        .minioObjectKey(destKey)
                        .createdAt(Instant.now())
                        .updatedAt(Instant.now())
                        .build();

                filesToSave.add(pf);
            }

            projectFileRepository.saveAll(filesToSave);
            log.info("Template initialized and file tree cache evicted for projectId={}", projectId);

        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize project from template", e);
        }
    }
}
