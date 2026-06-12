package com.aiassistant.userbusiness.services;

import com.aiassistant.userbusiness.exceptions.BusinessNotFoundException;
import com.aiassistant.userbusiness.exceptions.ConflictException;
import com.aiassistant.userbusiness.models.dao.BusinessNotificationRecipientEntity;
import com.aiassistant.userbusiness.models.request.AddNotificationRecipientRequest;
import com.aiassistant.userbusiness.models.response.NotificationRecipientResponse;
import com.aiassistant.userbusiness.repository.BusinessNotificationRecipientRepository;
import com.aiassistant.userbusiness.repository.BusinessRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

/**
 * Additional WhatsApp recipients beyond the business's primary signup number.
 * Primary stays on {@code businesses.whatsapp_number}; rows in
 * {@code business_notification_recipients} are extra people the owner wants
 * to keep in the loop (managers, reception, etc.).
 *
 * <p>Notification-service reads the union of (primary + active recipients
 * here) when dispatching owner pings.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationRecipientService {

    private static final Logger log = LoggerFactory.getLogger(NotificationRecipientService.class);

    private final BusinessNotificationRecipientRepository repository;
    private final BusinessRepository businessRepository;

    public List<NotificationRecipientResponse> list(String businessId) {
        requireBusiness(businessId);
        return repository.findAllByBusinessId(businessId).stream()
                .sorted(Comparator.comparing(BusinessNotificationRecipientEntity::getCreatedAt))
                .map(NotificationRecipientService::toResponse)
                .toList();
    }

    @Transactional
    public NotificationRecipientResponse add(String businessId, AddNotificationRecipientRequest req) {
        requireBusiness(businessId);
        String number = req.getWhatsappNumber().trim();

        // Block duplicate with primary signup number — that one already gets pinged.
        String primary = businessRepository.findById(businessId)
                .map(b -> b.getWhatsappNumber()).orElse(null);
        if (primary != null && primary.equals(number)) {
            throw new ConflictException("This number is already the primary owner WhatsApp number");
        }

        // Block duplicate within additional recipients.
        repository.findByBusinessIdAndWhatsappNumber(businessId, number).ifPresent(existing -> {
            throw new ConflictException("Recipient already exists: " + number);
        });

        BusinessNotificationRecipientEntity saved = repository.save(
                BusinessNotificationRecipientEntity.builder()
                        .businessId(businessId)
                        .whatsappNumber(number)
                        .label(req.getLabel())
                        .isActive(true)
                        .build());
        log.info("Notification recipient added businessId={} recipientId={} number={}",
                businessId, saved.getId(), number);
        return toResponse(saved);
    }

    @Transactional
    public void delete(String businessId, String recipientId) {
        BusinessNotificationRecipientEntity entity = repository
                .findByIdAndBusinessId(recipientId, businessId)
                .orElseThrow(() -> new BusinessNotFoundException(
                        "Recipient " + recipientId + " not found for business " + businessId));
        repository.delete(entity);
        log.info("Notification recipient removed businessId={} recipientId={}", businessId, recipientId);
    }

    private void requireBusiness(String businessId) {
        if (!businessRepository.existsById(businessId)) {
            throw new BusinessNotFoundException(businessId);
        }
    }

    private static NotificationRecipientResponse toResponse(BusinessNotificationRecipientEntity e) {
        return NotificationRecipientResponse.builder()
                .id(e.getId())
                .whatsappNumber(e.getWhatsappNumber())
                .label(e.getLabel())
                .isActive(e.getIsActive())
                .createdAt(e.getCreatedAt())
                .build();
    }
}
