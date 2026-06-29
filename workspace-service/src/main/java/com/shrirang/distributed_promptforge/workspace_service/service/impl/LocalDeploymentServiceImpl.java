package com.shrirang.distributed_promptforge.workspace_service.service.impl;

import com.shrirang.distributed_promptforge.workspace_service.dto.project.DeployResponse;
import com.shrirang.distributed_promptforge.workspace_service.service.DeploymentService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

// 
@Service
public class LocalDeploymentServiceImpl implements DeploymentService {

    @Value("${app.preview.base-url:http://localhost:9020/internal/v1/previews}")
    private String previewBaseUrl;

    @Value("${app.preview.url-template:}")
    private String previewUrlTemplate;

    @Override
    public DeployResponse deploy(Long projectId) {
        if (previewUrlTemplate != null && !previewUrlTemplate.isBlank()) {
            return new DeployResponse(
                    previewUrlTemplate.replace("{projectId}", String.valueOf(projectId))
            );
        }

        String normalizedBase = previewBaseUrl.endsWith("/")
                ? previewBaseUrl.substring(0, previewBaseUrl.length() - 1)
                : previewBaseUrl;
        return new DeployResponse(normalizedBase + "/" + projectId + "/index.html");
    }

}
