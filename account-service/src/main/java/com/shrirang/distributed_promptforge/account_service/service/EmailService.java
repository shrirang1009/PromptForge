package com.shrirang.distributed_promptforge.account_service.service;

public interface EmailService {
    void sendHtmlEmail(String to, String subject, String body);
    boolean isEmailValid(String email);
}
