package com.aiassistant.subscription.models.dao;

import de.huxhorn.sulky.ulid.ULID;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "payments")
public class PaymentEntity {

    @Id
    @Column(name = "id", length = 26)
    private String id;

    @Column(name = "business_id", nullable = false, length = 26)
    private String businessId;

    @Column(name = "subscription_id", length = 26)
    private String subscriptionId;

    @Column(name = "razorpay_payment_id", unique = true, length = 50)
    private String razorpayPaymentId;

    @Column(name = "razorpay_order_id", length = 50)
    private String razorpayOrderId;

    @Column(name = "amount", nullable = false)
    private int amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "payment_method", length = 20)
    private String paymentMethod;

    @Column(name = "gst_amount", nullable = false)
    private int gstAmount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.id == null) this.id = new ULID().nextULID();
        this.createdAt = Instant.now();
    }
}
