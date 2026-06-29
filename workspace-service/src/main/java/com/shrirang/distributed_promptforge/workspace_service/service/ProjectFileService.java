package com.shrirang.distributed_promptforge.workspace_service.service;


import com.mayur.distributed_promptforge.common_lib.dto.FileTreeDto;
import com.shrirang.distributed_promptforge.workspace_service.dto.project.FileContentResponse;

public interface ProjectFileService {
    FileTreeDto getFileTree(Long projectId);

    FileContentResponse getFileContent(Long projectId, String path);

    void saveFile(Long projectId, String filePath, String fileContent);

    byte[] downloadProjectZip(Long projectId);

    void deleteProjectFolder(Long projectId);
}
