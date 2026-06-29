package com.shrirang.distributed_promptforge.common_lib.enums;

import lombok.Getter;

import java.util.Set;

import static com.mayur.distributed_promptforge.common_lib.enums.ProjectPermission.*;

@Getter
public enum ProjectRole {

    EDITOR(VIEW, EDIT, VIEW_MEMBERS),
    VIEWER(VIEW, VIEW_MEMBERS),
    OWNER(VIEW, EDIT, DELETE, MANAGE_MEMBERS, VIEW_MEMBERS);

    private final Set<ProjectPermission> permissions;

    ProjectRole(ProjectPermission... permissions) {
        this.permissions = Set.of(permissions);
    }
}
