package com.shrirang.distributed_promptforge.workspace_service;

import com.mayur.distributed_promptforge.common_lib.security.JwtUserPrincipal;
import com.mayur.distributed_promptforge.common_lib.security.AuthUtil;
import com.shrirang.distributed_promptforge.workspace_service.service.ProjectService;
import com.shrirang.distributed_promptforge.workspace_service.dto.project.ProjectSummaryResponse;
import com.shrirang.distributed_promptforge.workspace_service.repository.ProjectRepository;
import com.shrirang.distributed_promptforge.workspace_service.repository.ProjectMemberRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.List;

@SpringBootTest
class WorkspaceServiceApplicationTests {

    @Autowired
    private ProjectService projectService;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ProjectMemberRepository projectMemberRepository;

    @Test
    void testGetUserProjectByIdWithOwner() {
        System.out.println("=== DIAGNOSING DATABASE PROJECTS AND MEMBERS ===");
        try {
            System.out.println("Listing all projects:");
            projectRepository.findAll().forEach(p -> {
                System.out.println("Project ID: " + p.getId() + ", Name: " + p.getName() + ", DeletedAt: " + p.getDeletedAt());
            });

            System.out.println("Listing all project members:");
            projectMemberRepository.findAll().forEach(m -> {
                System.out.println("Member: ProjectID=" + m.getId().getProjectId() + ", UserID=" + m.getId().getUserId() + ", Role=" + m.getProjectRole());
            });

            // Mock authentication for user_id = 2 (Mayur)
            JwtUserPrincipal principal = new JwtUserPrincipal(
                    2L, 
                    "Mayur", 
                    "mayur@gmail.com", 
                    "USER", 
                    null, 
                    List.of()
            );
            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                    principal, 
                    null, 
                    List.of()
            );
            SecurityContextHolder.getContext().setAuthentication(auth);

            for (long pId : new long[]{13L, 14L}) {
                System.out.println("Testing projectService.getUserProjectById(" + pId + ") for user_id = 2...");
                try {
                    ProjectSummaryResponse response = projectService.getUserProjectById(pId);
                    System.out.println("SUCCESS! Loaded project " + pId + " response: " + response);
                } catch (Exception e) {
                    System.out.println("FAILURE! Exception thrown for project " + pId + ": " + e.getClass().getName() + " - " + e.getMessage());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println("==================================================");
    }

    @Autowired
    private AuthUtil authUtil;

    @Test
    void testGenerateToken() {
        JwtUserPrincipal principal = new JwtUserPrincipal(
                2L, 
                "Mayur", 
                "mayur@gmail.com", 
                "USER", 
                null, 
                List.of()
        );
        String token = authUtil.generateAccessToken(principal);
        System.out.println("=== GENERATED JWT TOKEN ===");
        System.out.println(token);
        System.out.println("===========================");
    }

}
