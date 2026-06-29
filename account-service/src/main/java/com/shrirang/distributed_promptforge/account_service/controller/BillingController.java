package com.shrirang.distributed_promptforge.account_service.controller;

import com.mayur.distributed_promptforge.account_service.dto.subscription.*;
import com.shrirang.distributed_promptforge.account_service.dto.subscription.*;
import jakarta.validation.Valid;
import com.shrirang.distributed_promptforge.account_service.service.PaymentProcessor;
import com.shrirang.distributed_promptforge.account_service.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.List;

@RestController
@RequiredArgsConstructor
@Slf4j
public class BillingController {

    private final SubscriptionService subscriptionService;
    private final PaymentProcessor paymentProcessor;

    @Value("${razorpay.webhook.secret:}")
    private String webhookSecret;

    // ─── Subscription / Plan info ─────────────────────────────────────────────
    // Gateway strips /api/ prefix → these are called as /me/subscription etc.

    @GetMapping("/me/subscription")
    public ResponseEntity<List<SubscriptionResponse>> getMySubscription() {
        return ResponseEntity.ok(subscriptionService.getCurrentSubscriptions());
    }

    @PostMapping("/payments/subscriptions/{id}/cancel")
    public ResponseEntity<Void> cancelSubscription(@PathVariable Long id) {
        subscriptionService.cancelSubscriptionById(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me/usage-today")
    public ResponseEntity<UsageTodayResponse> getUsageToday() {
        return ResponseEntity.ok(subscriptionService.getUsageToday());
    }

    @GetMapping("/me/plan-limits")
    public ResponseEntity<PlanLimitsResponse> getPlanLimits() {
        return ResponseEntity.ok(subscriptionService.getPlanLimits());
    }

    @GetMapping("/plans")
    public ResponseEntity<List<PublicPlanResponse>> getPlans() {
        return ResponseEntity.ok(subscriptionService.getAvailablePlans());
    }

    // ─── Payments ─────────────────────────────────────────────────────────────

    @PostMapping("/payments/checkout")
    public ResponseEntity<CheckoutResponse> createCheckoutResponse(
            @RequestBody @Valid CheckoutRequest request) {
        return ResponseEntity.ok(paymentProcessor.createCheckoutSessionUrl(request));
    }

    @PostMapping("/payments/portal")
    public ResponseEntity<PortalResponse> openCustomerPortal() {
        return ResponseEntity.ok(paymentProcessor.openCustomerPortal());
    }

    @PostMapping("/payments/confirm")
    public ResponseEntity<Void> confirmPayment(@RequestBody @Valid ConfirmPaymentRequest request) {
        paymentProcessor.confirmPayment(request);
        return ResponseEntity.ok().build();
    }

    // ─── Razorpay Webhooks ────────────────────────────────────────────────────

    @PostMapping("/webhooks/payment")
    public ResponseEntity<String> handlePaymentWebhooks(
            @RequestBody String payload,
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature) {

        try {
            // Verify Razorpay webhook signature if webhookSecret is configured
            if (webhookSecret != null && !webhookSecret.trim().isEmpty()) {
                if (signature == null || signature.isBlank()) {
                    log.warn("Missing X-Razorpay-Signature header for webhook");
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Missing signature");
                }
                try {
                    boolean isValid = com.razorpay.Utils.verifyWebhookSignature(payload, signature, webhookSecret);
                    if (!isValid) {
                        log.warn("Invalid Razorpay webhook signature");
                        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid signature");
                    }
                } catch (Exception ex) {
                    log.warn("Razorpay webhook signature verification failed: {}", ex.getMessage());
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Signature verification error");
                }
            }

            // Parse the payload
            JSONObject razorpayEvent = new JSONObject(payload);
            String event = razorpayEvent.getString("event");

            // Get the payload object
            JSONObject payloadObj = razorpayEvent.optJSONObject("payload");
            JSONObject orderObj = payloadObj != null ? payloadObj.optJSONObject("order") : null;
            JSONObject paymentObj = payloadObj != null ? payloadObj.optJSONObject("payment") : null;
            JSONObject subscriptionObj = payloadObj != null ? payloadObj.optJSONObject("subscription") : null;

            // Determine which object to use based on event type
            JSONObject eventData = null;
            Map<String, String> metadata = new java.util.HashMap<>();

            if (orderObj != null && orderObj.has("attributes")) {
                eventData = orderObj.getJSONObject("attributes");
            } else if (paymentObj != null && paymentObj.has("attributes")) {
                eventData = paymentObj.getJSONObject("attributes");
            } else if (subscriptionObj != null && subscriptionObj.has("attributes")) {
                eventData = subscriptionObj.getJSONObject("attributes");
            }

            // Extract metadata from event data
            if (eventData != null && eventData.has("notes")) {
                JSONObject notes = eventData.getJSONObject("notes");
                metadata.put("user_id", notes.optString("user_id"));
                metadata.put("plan_id", notes.optString("plan_id"));
            }

            log.info("Processing Razorpay webhook event: {}", event);

            // Handle the webhook event
            paymentProcessor.handleWebhookEvent(event, eventData, metadata);

            return ResponseEntity.ok().build();

        } catch (Exception e) {
            log.error("Error processing webhook: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Webhook processing failed");
        }
    }
}
