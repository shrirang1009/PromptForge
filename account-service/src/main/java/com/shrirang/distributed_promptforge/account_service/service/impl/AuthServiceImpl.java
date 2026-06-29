package com.shrirang.distributed_promptforge.account_service.service.impl;

import com.mayur.distributed_promptforge.account_service.dto.auth.*;
import com.shrirang.distributed_promptforge.account_service.dto.auth.*;
import com.shrirang.distributed_promptforge.account_service.entity.User;
import com.shrirang.distributed_promptforge.account_service.mapper.UserMapper;
import com.shrirang.distributed_promptforge.account_service.repository.UserRepository;
import com.shrirang.distributed_promptforge.account_service.service.AuthService;
import com.shrirang.distributed_promptforge.account_service.service.EmailService;
import com.shrirang.distributed_promptforge.account_service.service.JwtBlacklistService;
import com.mayur.distributed_promptforge.common_lib.error.BadRequestException;
import com.mayur.distributed_promptforge.common_lib.event.EmailEvent;
import com.mayur.distributed_promptforge.common_lib.security.AuthUtil;
import com.mayur.distributed_promptforge.common_lib.security.JwtUserPrincipal;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import com.shrirang.distributed_promptforge.account_service.service.OtpCacheService;

import java.util.ArrayList;

@Service
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@Slf4j
public class AuthServiceImpl implements AuthService {

    UserRepository userRepository;
    UserMapper userMapper;
    PasswordEncoder passwordEncoder;
    AuthUtil authUtil;
    AuthenticationManager authenticationManager;
    JwtBlacklistService jwtBlacklistService;
    EmailService emailService;
    OtpCacheService otpCacheService;
    KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    public AuthResponse signup(SignupRequest request) {
        throw new UnsupportedOperationException("Use the new signup verification flow");
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password())
        );

        JwtUserPrincipal user = (JwtUserPrincipal) authentication.getPrincipal();
        String token = authUtil.generateAccessToken(user);

        return new AuthResponse(token, userMapper.toUserProfileResponse(user));
    }

    @Override
    public void logout(String token) {
        jwtBlacklistService.blacklist(token);
    }

    @Override
    public void forgotPassword(ForgotPasswordRequest request) {
        if (!emailService.isEmailValid(request.email())) {
            throw new BadRequestException("Email can not be sent. '" + request.email() + "' does not exist or is incorrect. Please enter a correct email address.");
        }

        User user = userRepository.findByUsernameIgnoreCase(request.email().trim())
                .orElseThrow(() -> new BadRequestException("No account found for email: " + request.email() + ". Please check the address or sign up."));

        String otp = generateOtp();
        user.setPasswordResetCode(otp);
        user.setPasswordResetCodeExpiresAt(java.time.Instant.now().plus(15, java.time.temporal.ChronoUnit.MINUTES));
        userRepository.save(user);

        try {
            sendForgotPasswordEmail(user.getUsername(), otp);
        } catch (Exception e) {
            log.error("OTP email delivery failed for forgot-password (email={}): {}", user.getUsername(), e.getMessage());
            // Clear the OTP so the stale code isn't left in DB
            user.setPasswordResetCode(null);
            user.setPasswordResetCodeExpiresAt(null);
            userRepository.save(user);
            throw new BadRequestException("We could not send the reset email. Please try again later.");
        }
    }

    @Override
    public void resetPassword(ResetPasswordRequest request) {
        User user = userRepository.findByUsernameIgnoreCase(request.email().trim())
                .orElseThrow(() -> new BadRequestException("Invalid request"));

        if (user.getPasswordResetCode() == null || !user.getPasswordResetCode().equals(request.code())) {
            throw new BadRequestException("Invalid reset code");
        }

        if (user.getPasswordResetCodeExpiresAt() == null || user.getPasswordResetCodeExpiresAt().isBefore(java.time.Instant.now())) {
            throw new BadRequestException("Reset code has expired");
        }

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        user.setPasswordResetCode(null);
        user.setPasswordResetCodeExpiresAt(null);
        userRepository.save(user);
    }

    @Override
    public void verifyResetCode(VerifyOtpRequest request) {
        User user = userRepository.findByUsernameIgnoreCase(request.email().trim())
                .orElseThrow(() -> new BadRequestException("Invalid request"));

        if (user.getPasswordResetCode() == null || !user.getPasswordResetCode().equals(request.code())) {
            throw new BadRequestException("Invalid reset code");
        }

        if (user.getPasswordResetCodeExpiresAt() == null || user.getPasswordResetCodeExpiresAt().isBefore(java.time.Instant.now())) {
            throw new BadRequestException("Reset code has expired");
        }
    }

    @Override
    public void sendSignupOtp(SignupOtpRequest request) {
        if (!emailService.isEmailValid(request.email())) {
            throw new BadRequestException("Email can not be sent. '" + request.email() + "' does not exist or is incorrect. Please enter a correct email address.");
        }

        userRepository.findByUsernameIgnoreCase(request.email().trim()).ifPresent(user -> {
            throw new BadRequestException("An account already exists with email: " + request.email());
        });

        String otp = generateOtp();
        otpCacheService.saveSignupOtp(request.email().toLowerCase(), otp);

        try {
            sendVerificationEmail(request.email().toLowerCase(), otp);
        } catch (Exception e) {
            log.error("OTP email delivery failed for signup (email={}): {}", request.email(), e.getMessage());
            otpCacheService.deleteSignupOtp(request.email().toLowerCase());
            throw new BadRequestException("We could not send the verification email. Please check the address and try again.");
        }
    }

    @Override
    public void verifySignupOtp(VerifyOtpRequest request) {
        String savedOtp = otpCacheService.getSignupOtp(request.email().toLowerCase());

        if (savedOtp == null || !savedOtp.equals(request.code())) {
            throw new BadRequestException("Invalid or expired verification code");
        }

        // OTP verified — delete it so it can't be reused
        otpCacheService.deleteSignupOtp(request.email().toLowerCase());

        otpCacheService.saveSignupVerified(request.email().toLowerCase());
    }

    @Override
    public AuthResponse completeSignup(SignupCompleteRequest request) {
        String isVerified = otpCacheService.getSignupVerified(request.email().toLowerCase());

        if (isVerified == null || !isVerified.equals("true")) {
            throw new BadRequestException("Email verification required. Please complete the OTP step first.");
        }

        userRepository.findByUsernameIgnoreCase(request.email().trim()).ifPresent(user -> {
            throw new BadRequestException("An account already exists with email: " + request.email());
        });

        User user = User.builder()
                .username(request.email().toLowerCase())
                .name(request.name().trim())
                .password(passwordEncoder.encode(request.password()))
                .emailVerified(true)
                .build();

        user = userRepository.save(user);

        // Clean up verified flag
        otpCacheService.deleteSignupVerified(request.email().toLowerCase());

        JwtUserPrincipal jwtUserPrincipal = new JwtUserPrincipal(
                user.getId(), user.getName(), user.getUsername(),
                user.getRole().name(), null, new ArrayList<>()
        );

        String token = authUtil.generateAccessToken(jwtUserPrincipal);
        return new AuthResponse(token, userMapper.toUserProfileResponse(jwtUserPrincipal));
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private String generateOtp() {
        // SecureRandom for cryptographically strong OTP generation
        java.security.SecureRandom random = new java.security.SecureRandom();
        int otp = 100000 + random.nextInt(900000);
        return String.valueOf(otp);
    }

    private void sendVerificationEmail(String email, String otp) {
        String subject = "Verify your PromptForge Account";
        String body = """
                <div style="font-family:Arial,sans-serif;max-width:600px;margin:0 auto;padding:20px;
                            border:1px solid #e0e0e0;border-radius:10px;">
                  <h2 style="color:#6366f1;text-align:center;">Welcome to PromptForge!</h2>
                  <p>Thank you for signing up. Please verify your email using the OTP below:</p>
                  <div style="text-align:center;margin:30px 0;">
                    <span style="font-size:32px;font-weight:bold;letter-spacing:5px;padding:10px 20px;
                                 background:#f3f4f6;border-radius:5px;border:1px solid #d1d5db;
                                 color:#1f2937;">%s</span>
                  </div>
                  <p style="color:#6b7280;font-size:14px;">
                    This code is valid for 15 minutes. If you did not request this, please ignore this email.
                  </p>
                </div>
                """.formatted(otp);
        publishEmailEvent(email, subject, body);
    }

    private void sendForgotPasswordEmail(String email, String otp) {
        String subject = "Reset your PromptForge Password";
        String body = """
                <div style="font-family:Arial,sans-serif;max-width:600px;margin:0 auto;padding:20px;
                            border:1px solid #e0e0e0;border-radius:10px;">
                  <h2 style="color:#6366f1;text-align:center;">Reset your Password</h2>
                  <p>We received a request to reset your password. Use the OTP below to complete the reset:</p>
                  <div style="text-align:center;margin:30px 0;">
                    <span style="font-size:32px;font-weight:bold;letter-spacing:5px;padding:10px 20px;
                                 background:#f3f4f6;border-radius:5px;border:1px solid #d1d5db;
                                 color:#1f2937;">%s</span>
                  </div>
                  <p style="color:#6b7280;font-size:14px;">
                    This code is valid for 15 minutes. If you did not request a password reset, please ignore this email.
                  </p>
                </div>
                """.formatted(otp);
        publishEmailEvent(email, subject, body);
    }

    private void publishEmailEvent(String to, String subject, String body) {
        EmailEvent emailEvent = new EmailEvent(to, subject, body);
        kafkaTemplate.send("email-send-event", to, emailEvent)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish email event to Kafka for {}: {}", to, ex.getMessage(), ex);
                    } else {
                        log.info("Email event published to Kafka for: {}", to);
                    }
                });
    }
}
