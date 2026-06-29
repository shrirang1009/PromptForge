package com.shrirang.distributed_promptforge.workspace_service.config;

/**
 * Central registry of all Redis key prefixes used in workspace-service.
 *
 * Having a single source of truth for key names prevents silent bugs that
 * arise when two classes define the same prefix as a local constant and one
 * of them is later changed without updating the other.
 *
 * Usage:
 *   redisTemplate.delete(RedisKeys.FILE_TREE_KEY_PREFIX + projectId);
 */
public final class RedisKeys {

    private RedisKeys() {}

    /**
     * Per-project file-tree cache.
     * Full key: filetree:project:{projectId}
     * TTL: 45 seconds (see ProjectFileServiceImpl)
     */
    public static final String FILE_TREE_KEY_PREFIX = "filetree:project:";
}
