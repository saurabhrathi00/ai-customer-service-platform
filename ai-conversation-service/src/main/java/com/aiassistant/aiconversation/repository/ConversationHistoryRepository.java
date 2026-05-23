package com.aiassistant.aiconversation.repository;

import com.aiassistant.aiconversation.models.dao.ConversationHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationHistoryRepository extends JpaRepository<ConversationHistoryEntity, String> {
    boolean existsByConversationId(String conversationId);
}
