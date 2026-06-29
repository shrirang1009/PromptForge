package com.shrirang.distributed_promptforge.account_service.service;

import com.shrirang.distributed_promptforge.account_service.dto.subscription.CheckoutRequest;
import com.shrirang.distributed_promptforge.account_service.dto.subscription.CheckoutResponse;
import com.shrirang.distributed_promptforge.account_service.dto.subscription.ConfirmPaymentRequest;
import com.shrirang.distributed_promptforge.account_service.dto.subscription.PortalResponse;
import org.json.JSONObject;

import java.util.Map;

public interface PaymentProcessor {

    CheckoutResponse createCheckoutSessionUrl(CheckoutRequest request);
    void confirmPayment(ConfirmPaymentRequest request);

    PortalResponse openCustomerPortal();

    void handleWebhookEvent(String type, JSONObject razorpayObject, Map<String, String> metadata);
}
