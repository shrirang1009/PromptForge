package com.shrirang.distributed_promptforge.account_service.service;


import com.mayur.distributed_promptforge.account_service.dto.auth.*;
import com.shrirang.distributed_promptforge.account_service.dto.auth.*;

public interface AuthService {
    AuthResponse signup(SignupRequest request);

    AuthResponse login(LoginRequest request);

    void logout(String token);

    void forgotPassword(ForgotPasswordRequest request);

    void resetPassword(ResetPasswordRequest request);

    void verifyResetCode(VerifyOtpRequest request);

    void sendSignupOtp(SignupOtpRequest request);

    void verifySignupOtp(VerifyOtpRequest request);

    AuthResponse completeSignup(SignupCompleteRequest request);
}
