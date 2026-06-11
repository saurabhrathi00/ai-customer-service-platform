package com.aiassistant.aiconversation.repository;

import com.aiassistant.aiconversation.models.dao.GeminiPromptCacheEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GeminiPromptCacheRepository extends JpaRepository<GeminiPromptCacheEntity, String> {
    Optional<GeminiPromptCacheEntity> findByBusinessId(String businessId);
    void deleteByBusinessId(String businessId);
}
