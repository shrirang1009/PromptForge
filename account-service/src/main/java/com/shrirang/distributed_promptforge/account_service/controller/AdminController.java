package com.shrirang.distributed_promptforge.account_service.controller;

import com.shrirang.distributed_promptforge.account_service.dto.admin.AdminDashboardResponse;
import com.shrirang.distributed_promptforge.account_service.dto.admin.AdminPlanUpsertRequest;
import com.shrirang.distributed_promptforge.account_service.dto.admin.AdminUserResponse;
import com.shrirang.distributed_promptforge.account_service.dto.subscription.PublicPlanResponse;
import com.shrirang.distributed_promptforge.account_service.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/dashboard")
    public ResponseEntity<AdminDashboardResponse> getDashboard() {
        return ResponseEntity.ok(adminService.getDashboard());
    }

    @GetMapping("/users")
    public ResponseEntity<List<AdminUserResponse>> getUsers(@RequestParam(required = false) String q) {
        return ResponseEntity.ok(adminService.getUsers(q));
    }

    @PatchMapping("/users/{userId}/block")
    public ResponseEntity<Void> blockUser(@PathVariable Long userId, @RequestParam boolean blocked) {
        adminService.setUserBlocked(userId, blocked);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/users/{userId}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long userId) {
        adminService.deleteUser(userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/plans")
    public ResponseEntity<List<PublicPlanResponse>> getPlans() {
        return ResponseEntity.ok(adminService.getPlans());
    }

    @PatchMapping("/plans/{planId}/active")
    public ResponseEntity<Void> setPlanActive(@PathVariable Long planId, @RequestParam boolean active) {
        adminService.activatePlan(planId, active);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/plans")
    public ResponseEntity<Void> createPlan(@RequestBody AdminPlanUpsertRequest request) {
        adminService.createPlan(request);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/plans/{planId}")
    public ResponseEntity<Void> updatePlan(@PathVariable Long planId, @RequestBody AdminPlanUpsertRequest request) {
        adminService.updatePlan(planId, request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/plans/{planId}")
    public ResponseEntity<Void> deletePlan(@PathVariable Long planId) {
        adminService.deletePlan(planId);
        return ResponseEntity.noContent().build();
    }
}
