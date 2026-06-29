package com.shrirang.distributed_promptforge.workspace_service.service;

import com.shrirang.distributed_promptforge.workspace_service.dto.project.DeployResponse;
import org.jspecify.annotations.Nullable;

public interface DeploymentService {
    @Nullable DeployResponse deploy(Long projectId);
}
