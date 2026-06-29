package com.shrirang.distributed_promptforge.account_service.consumer;

import com.shrirang.distributed_promptforge.account_service.service.EmailService;
import com.mayur.distributed_promptforge.common_lib.event.EmailEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class EmailConsumer {

    private final EmailService emailService;

    @KafkaListener(topics = "email-send-event", groupId = "account-group")
    public void consumeEmailEvent(EmailEvent emailEvent) {
        log.info("Received email event for: {}", emailEvent.to());
        try {
            emailService.sendHtmlEmail(emailEvent.to(), emailEvent.subject(), emailEvent.body());
        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", emailEvent.to(), e.getMessage(), e);
        }
    }
}
