package com.shrirang.distributed_promptforge.account_service.service;

import com.shrirang.distributed_promptforge.account_service.dto.admin.AdminDashboardResponse;
import com.shrirang.distributed_promptforge.account_service.dto.admin.AdminPlanUpsertRequest;
import com.shrirang.distributed_promptforge.account_service.dto.admin.AdminUserResponse;
import com.shrirang.distributed_promptforge.account_service.dto.subscription.PublicPlanResponse;

import java.util.List;

public interface AdminService {
    AdminDashboardResponse getDashboard();
    List<AdminUserResponse> getUsers(String query);
    void setUserBlocked(Long userId, boolean blocked);
    void deleteUser(Long userId);
    List<PublicPlanResponse> getPlans();
    void activatePlan(Long planId, boolean active);
    void createPlan(AdminPlanUpsertRequest request);
    void updatePlan(Long planId, AdminPlanUpsertRequest request);
    void deletePlan(Long planId);
}
