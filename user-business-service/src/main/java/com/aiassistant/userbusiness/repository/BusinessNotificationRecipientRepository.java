package com.aiassistant.userbusiness.repository;

import com.aiassistant.userbusiness.models.dao.BusinessNotificationRecipientEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BusinessNotificationRecipientRepository
        extends JpaRepository<BusinessNotificationRecipientEntity, String> {

    List<BusinessNotificationRecipientEntity> findAllByBusinessId(String businessId);

    List<BusinessNotificationRecipientEntity> findAllByBusinessIdAndIsActiveTrue(String businessId);

    Optional<BusinessNotificationRecipientEntity> findByBusinessIdAndWhatsappNumber(
            String businessId, String whatsappNumber);

    Optional<BusinessNotificationRecipientEntity> findByIdAndBusinessId(String id, String businessId);
}
