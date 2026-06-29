package com.shrirang.distributed_promptforge.account_service.config;

import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class PaymentConfig {

    @Value("${razorpay.key.id:}")
    private String razorpayKeyId;

    @Value("${razorpay.key.secret:}")
    private String razorpayKeySecret;

    @PostConstruct
    public void init() {
        if (razorpayKeyId != null && !razorpayKeyId.isBlank() && razorpayKeySecret != null && !razorpayKeySecret.isBlank()) {
            try {
                new RazorpayClient(razorpayKeyId, razorpayKeySecret);
                log.info("Razorpay client initialized successfully");
            } catch (RazorpayException e) {
                log.error("Failed to initialize Razorpay client: {}", e.getMessage());
            }
        } else {
            log.warn("Razorpay keys not configured - payment features will be unavailable");
        }
    }
}