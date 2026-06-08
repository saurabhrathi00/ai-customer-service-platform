package com.aiassistant.subscription.repository;

import com.aiassistant.subscription.models.dao.UsageEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UsageEventRepository extends JpaRepository<UsageEventEntity, String> {
    boolean existsByCallId(String callId);
}
