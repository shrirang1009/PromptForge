package com.shrirang.distributed_promptforge.account_service.config;

import com.shrirang.distributed_promptforge.account_service.entity.User;
import com.shrirang.distributed_promptforge.account_service.entity.UserRole;
import com.shrirang.distributed_promptforge.account_service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@RequiredArgsConstructor
public class AdminBootstrapConfig {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.email:admin@promptforge.com}")
    private String adminEmail;

    @Value("${app.admin.password:Admin@123}")
    private String adminPassword;

    @Bean
    public CommandLineRunner ensureAdminUser() {
        return args -> userRepository.findByUsernameIgnoreCase(adminEmail)
                .ifPresentOrElse(user -> {
                    if (user.getRole() != UserRole.ADMIN) {
                        user.setRole(UserRole.ADMIN);
                        userRepository.save(user);
                    }
                }, () -> {
                    User admin = User.builder()
                            .username(adminEmail)
                            .name("System Admin")
                            .password(passwordEncoder.encode(adminPassword))
                            .role(UserRole.ADMIN)
                            .blocked(false)
                            .emailVerified(true)
                            .build();
                    userRepository.save(admin);
                });
    }
}
