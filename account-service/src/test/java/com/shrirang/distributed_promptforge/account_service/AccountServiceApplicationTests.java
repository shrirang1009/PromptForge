package com.shrirang.distributed_promptforge.account_service;

import com.shrirang.distributed_promptforge.account_service.service.EmailService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.List;
import java.util.Map;

@SpringBootTest
class AccountServiceApplicationTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EmailService emailService;

    @Test
    void contextLoads() {
        System.out.println("=== DIAGNOSING DATABASE USERS ===");
        try {
            List<Map<String, Object>> users = jdbcTemplate.queryForList("SELECT * FROM users");
            System.out.println("Users count: " + users.size());
            for (Map<String, Object> u : users) {
                System.out.println("User: " + u);
            }
        } catch (Exception e) {
            System.out.println("Failed to query users: " + e.getMessage());
        }
        System.out.println("==================================");

        System.out.println("=== TESTING EMAIL VALIDATION ===");
        try {
            String testEmail = "msonagra787787@gmail.com";
            boolean exists = emailService.isEmailValid(testEmail);
            System.out.println("Result for " + testEmail + ": " + exists);
        } catch (Exception e) {
            System.out.println("Email validation failed: " + e.getMessage());
            e.printStackTrace();
        }
        System.out.println("================================");
    }

}
