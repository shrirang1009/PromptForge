package com.shrirang.distributed_promptforge.workspace_service.mapper;

import com.mayur.distributed_promptforge.common_lib.dto.FileNode;
import com.shrirang.distributed_promptforge.workspace_service.entity.ProjectFile;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ProjectFileMapper {

    public FileNode toFileNode(ProjectFile projectFile) {
        if (projectFile == null) {
            return null;
        }

        return new FileNode(projectFile.getPath());
    }

    public List<FileNode> toListOfFileNode(List<ProjectFile> projectFileList) {
        if (projectFileList == null) {
            return null;
        }

        return projectFileList.stream()
                .map(this::toFileNode)
                .toList();
    }
}
