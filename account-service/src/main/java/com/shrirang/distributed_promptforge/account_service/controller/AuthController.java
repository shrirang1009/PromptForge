package com.shrirang.distributed_promptforge.account_service.controller;

import com.mayur.distributed_promptforge.account_service.dto.auth.*;
import com.shrirang.distributed_promptforge.account_service.dto.auth.*;
import com.shrirang.distributed_promptforge.account_service.service.AuthService;
import com.mayur.distributed_promptforge.common_lib.security.JwtUserPrincipal;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/auth")
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class AuthController {

    AuthService authService;

    @PostMapping("/signup/send-code")
    public ResponseEntity<Void> sendSignupOtp(@RequestBody @Valid SignupOtpRequest request) {
        authService.sendSignupOtp(request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/signup/verify-code")
    public ResponseEntity<Void> verifySignupOtp(@RequestBody @Valid VerifyOtpRequest request) {
        authService.verifySignupOtp(request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/signup/complete")
    public ResponseEntity<AuthResponse> completeSignup(@RequestBody @Valid SignupCompleteRequest request) {
        return ResponseEntity.ok(authService.completeSignup(request));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@RequestBody @Valid ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@RequestBody @Valid ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/verify-reset-code")
    public ResponseEntity<Void> verifyResetCode(@RequestBody @Valid VerifyOtpRequest request) {
        authService.verifyResetCode(request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody @Valid LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader("Authorization") String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            authService.logout(authHeader.substring(7));
        }
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getProfile() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof JwtUserPrincipal user)) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(new UserProfileResponse(user.userId(), user.username(), user.name(), user.role()));
    }
}
