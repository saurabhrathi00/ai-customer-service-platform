package com.aiassistant.subscription.repository;

import com.aiassistant.subscription.models.dao.PaymentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentRepository extends JpaRepository<PaymentEntity, String> {
    List<PaymentEntity> findByBusinessIdOrderByCreatedAtDesc(String businessId);
    boolean existsByRazorpayPaymentId(String razorpayPaymentId);
}
