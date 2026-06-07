package com.aiassistant.subscription.models.response;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value
@Builder
@Jacksonized
public class CheckoutResponse {
    String subscriptionId;
    String razorpaySubscriptionId;
    String razorpayKeyId;
}
