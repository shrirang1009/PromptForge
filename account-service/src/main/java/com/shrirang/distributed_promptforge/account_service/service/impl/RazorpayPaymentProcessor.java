package com.shrirang.distributed_promptforge.account_service.service.impl;

import com.shrirang.distributed_promptforge.account_service.dto.subscription.CheckoutRequest;
import com.shrirang.distributed_promptforge.account_service.dto.subscription.CheckoutResponse;
import com.shrirang.distributed_promptforge.account_service.dto.subscription.ConfirmPaymentRequest;
import com.shrirang.distributed_promptforge.account_service.dto.subscription.PortalResponse;
import com.shrirang.distributed_promptforge.account_service.entity.Plan;
import com.shrirang.distributed_promptforge.account_service.entity.User;
import com.shrirang.distributed_promptforge.account_service.repository.PlanRepository;
import com.shrirang.distributed_promptforge.account_service.repository.UserRepository;
import com.shrirang.distributed_promptforge.account_service.service.PaymentProcessor;
import com.shrirang.distributed_promptforge.account_service.service.SubscriptionService;
import com.mayur.distributed_promptforge.common_lib.error.ResourceNotFoundException;
import com.mayur.distributed_promptforge.common_lib.security.AuthUtil;
import com.razorpay.Order;
import com.razorpay.Payment;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class RazorpayPaymentProcessor implements PaymentProcessor {

    private final AuthUtil authUtil;
    private final PlanRepository planRepository;
    private final UserRepository userRepository;
    private final SubscriptionService subscriptionService;

    @Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    @Value("${razorpay.key.id}")
    private String razorpayKeyId;

    @Value("${razorpay.key.secret}")
    private String razorpayKeySecret;

    private RazorpayClient getRazorpayClient() throws RazorpayException {
        return new RazorpayClient(razorpayKeyId, razorpayKeySecret);
    }

    @Override
    public CheckoutResponse createCheckoutSessionUrl(CheckoutRequest request) {
        Plan plan = planRepository.findById(request.planId()).orElseThrow(() ->
                new ResourceNotFoundException("Plan", request.planId().toString()));

        Long userId = authUtil.getCurrentUserId();
        User user = userRepository.findById(userId).orElseThrow(() ->
                new ResourceNotFoundException("user", userId.toString()));

        try {
            RazorpayClient razorpay = getRazorpayClient();

            // Use price from plan, default to 2000 paise (₹20) if not set
            long amountInPaise = plan.getPriceInPaise() != null ? plan.getPriceInPaise() : 2000L;

            JSONObject orderRequest = new JSONObject();
            orderRequest.put("amount", amountInPaise);
            orderRequest.put("currency", "INR");
            orderRequest.put("receipt", "rcpt_" + userId + "_" + System.currentTimeMillis());
            orderRequest.put("payment_capture", 1); // Auto capture

            JSONObject notes = new JSONObject();
            notes.put("user_id", userId.toString());
            notes.put("plan_id", plan.getId().toString());
            notes.put("plan_name", plan.getName());
            orderRequest.put("notes", notes);

            Order order = razorpay.orders.create(orderRequest);

            String orderId = order.get("id");
            log.info("Created Razorpay order {} for user {} plan {}", orderId, userId, plan.getId());

            String checkoutUrl = "https://checkout.razorpay.com/v1/checkout.js";
            return new CheckoutResponse(
                    checkoutUrl,
                    orderId,
                    razorpayKeyId,
                    amountInPaise,
                    "INR",
                    user.getName(),
                    user.getUsername()
            );

        } catch (RazorpayException e) {
            log.error("Razorpay error: {}", e.getMessage());
            throw new RuntimeException("Payment initialization failed: " + e.getMessage());
        }
    }

    @Override
    public void confirmPayment(ConfirmPaymentRequest request) {
        try {
            RazorpayClient razorpay = getRazorpayClient();
            Order order = razorpay.orders.fetch(request.orderId());
            Payment payment = razorpay.payments.fetch(request.paymentId());

            String paymentOrderId = payment.get("order_id");
            if (!request.orderId().equals(paymentOrderId)) {
                throw new RuntimeException("Payment does not belong to the requested order");
            }

            JSONObject orderJson = new JSONObject(order.toString());
            JSONObject notes = orderJson.optJSONObject("notes");
            if (notes == null) {
                throw new RuntimeException("Missing payment metadata in Razorpay order notes");
            }

            String userIdStr = notes.optString("user_id");
            String planIdStr = notes.optString("plan_id");
            if (userIdStr == null || userIdStr.isBlank() || planIdStr == null || planIdStr.isBlank()) {
                throw new RuntimeException("Invalid payment metadata in Razorpay order");
            }

            subscriptionService.activateSubscription(
                    Long.parseLong(userIdStr),
                    Long.parseLong(planIdStr),
                    request.orderId(),
                    request.paymentId()
            );
            log.info("Payment confirmed and subscription activated for order {}", request.orderId());

        } catch (RazorpayException e) {
            log.error("Razorpay confirmation error: {}", e.getMessage());
            throw new RuntimeException("Payment confirmation failed: " + e.getMessage());
        }
    }

    @Override
    public PortalResponse openCustomerPortal() {
        Long userId = authUtil.getCurrentUserId();
        User user = getUser(userId);

        // For Razorpay, we return billing management URL
        String portalUrl = frontendUrl + "/billing?manage=1";

        log.info("Opening portal for user {} - redirecting to billing management", userId);

        return new PortalResponse(portalUrl);
    }

    @Override
    public void handleWebhookEvent(String type, JSONObject razorpayObject, Map<String, String> metadata) {
        log.debug("Handling Razorpay event: {}", type);

        try {
            switch (type) {
                case "order.paid" -> handleOrderPaid(razorpayObject);
                case "payment.captured" -> handlePaymentCaptured(razorpayObject);
                case "subscription.activated" -> handleSubscriptionActivated(razorpayObject);
                case "subscription.cancelled" -> handleSubscriptionCancelled(razorpayObject);
                case "subscription.paused" -> handleSubscriptionPaused(razorpayObject);
                default -> log.debug("Ignoring Razorpay event: {}", type);
            }
        } catch (Exception e) {
            log.error("Error handling Razorpay webhook: {}", e.getMessage());
            throw new RuntimeException("Webhook handling failed: " + e.getMessage());
        }
    }

    private void handleOrderPaid(JSONObject orderJson) {
        log.info("Handling order.paid webhook");

        try {
            JSONObject notes = orderJson.optJSONObject("notes");
            if (notes != null) {
                String userIdStr = notes.optString("user_id");
                String planIdStr = notes.optString("plan_id");

                if (userIdStr != null && !userIdStr.isEmpty() && planIdStr != null && !planIdStr.isEmpty()) {
                    String orderId = orderJson.getString("id");

                    subscriptionService.activateSubscription(
                            Long.parseLong(userIdStr),
                            Long.parseLong(planIdStr),
                            orderId,
                            orderId
                    );
                    log.info("Activated subscription from order.paid for order {}", orderId);
                }
            }
        } catch (Exception e) {
            log.error("Error in handleOrderPaid: {}", e.getMessage());
        }
    }

    private void handlePaymentCaptured(JSONObject paymentJson) {
        log.info("Handling payment.captured webhook");

        try {
            JSONObject notes = paymentJson.optJSONObject("notes");
            String userIdStr = notes != null ? notes.optString("user_id") : null;
            String planIdStr = notes != null ? notes.optString("plan_id") : null;

            if (userIdStr != null && !userIdStr.isEmpty() && planIdStr != null && !planIdStr.isEmpty()) {
                String paymentId = paymentJson.getString("id");

                subscriptionService.activateSubscription(
                        Long.parseLong(userIdStr),
                        Long.parseLong(planIdStr),
                        paymentId,
                        paymentId
                );
            }
        } catch (Exception e) {
            log.error("Error in handlePaymentCaptured: {}", e.getMessage());
        }
    }

    private void handleSubscriptionActivated(JSONObject subJson) {
        log.info("Handling subscription.activated webhook");

        try {
            String userIdStr = subJson.optString("notes.user_id");
            String planIdStr = subJson.optString("notes.plan_id");

            if (userIdStr != null && planIdStr != null) {
                String subscriptionId = subJson.getString("id");

                subscriptionService.activateSubscription(
                        Long.parseLong(userIdStr),
                        Long.parseLong(planIdStr),
                        subscriptionId,
                        null
                );
            }
        } catch (Exception e) {
            log.error("Error in handleSubscriptionActivated: {}", e.getMessage());
        }
    }

    private void handleSubscriptionCancelled(JSONObject subJson) {
        log.info("Handling subscription.cancelled webhook");

        try {
            String subscriptionId = subJson.getString("id");
            subscriptionService.cancelSubscription(subscriptionId);
        } catch (Exception e) {
            log.error("Error in handleSubscriptionCancelled: {}", e.getMessage());
        }
    }

    private void handleSubscriptionPaused(JSONObject subJson) {
        log.info("Handling subscription.paused webhook");

        try {
            String subscriptionId = subJson.getString("id");
            subscriptionService.markSubscriptionPastDue(subscriptionId);
        } catch (Exception e) {
            log.error("Error in handleSubscriptionPaused: {}", e.getMessage());
        }
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId).orElseThrow(() ->
                new ResourceNotFoundException("user", userId.toString()));
    }
}
