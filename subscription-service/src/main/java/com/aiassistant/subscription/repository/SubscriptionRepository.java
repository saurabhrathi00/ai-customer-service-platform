package com.aiassistant.subscription.repository;

import com.aiassistant.subscription.models.dao.SubscriptionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SubscriptionRepository extends JpaRepository<SubscriptionEntity, String> {
    Optional<SubscriptionEntity> findByBusinessIdAndStatusIn(String businessId, List<String> statuses);
    Optional<SubscriptionEntity> findByRazorpaySubscriptionId(String razorpaySubscriptionId);
    List<SubscriptionEntity> findAllByOrderByCreatedAtDesc();
}
