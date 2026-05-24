package com.aiassistant.userbusiness.repository;

import com.aiassistant.userbusiness.models.dao.LeadNotificationSettingsEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeadNotificationSettingsRepository
        extends JpaRepository<LeadNotificationSettingsEntity, String> {
}
