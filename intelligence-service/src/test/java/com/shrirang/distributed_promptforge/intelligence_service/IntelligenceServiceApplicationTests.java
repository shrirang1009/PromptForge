package com.shrirang.distributed_promptforge.intelligence_service;

import com.mayur.distributed_promptforge.common_lib.enums.ProjectPermission;
import com.shrirang.distributed_promptforge.intelligence_service.client.WorkspaceClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class IntelligenceServiceApplicationTests {

    @Autowired
    private WorkspaceClient workspaceClient;

    @Test
    void testCheckPermissionFeignClient() {
        System.out.println("=== DIAGNOSING FEIGN CLIENT IN INTELLIGENCE SERVICE ===");
        try {
            System.out.println("Calling workspaceClient.checkPermission(13L, VIEW, 2L)...");
            boolean allowed = workspaceClient.checkPermission(13L, ProjectPermission.VIEW, 2L);
            System.out.println("SUCCESS! Allowed: " + allowed);
        } catch (Exception e) {
            System.out.println("FAILURE! Exception thrown: " + e.getClass().getName() + " - " + e.getMessage());
            e.printStackTrace();
        }
        System.out.println("========================================================");
    }

}
