package com.aiassistant.subscription.repository;

import com.aiassistant.subscription.models.dao.PlanEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlanRepository extends JpaRepository<PlanEntity, String> {
    List<PlanEntity> findByIsActiveTrueOrderByDisplayOrderAsc();
    List<PlanEntity> findAllByOrderByDisplayOrderAsc();
    Optional<PlanEntity> findBySlug(String slug);
}
