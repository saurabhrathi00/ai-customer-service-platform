package com.aiassistant.userbusiness.services;

import com.aiassistant.userbusiness.exceptions.AppException;
import com.aiassistant.userbusiness.exceptions.BusinessNotFoundException;
import com.aiassistant.userbusiness.exceptions.ConflictException;
import com.aiassistant.userbusiness.models.dao.BusinessEntity;
import com.aiassistant.userbusiness.models.dao.BusinessPhoneNumberEntity;
import com.aiassistant.userbusiness.models.dao.ProviderPhoneNumberEntity;
import com.aiassistant.userbusiness.models.dao.RatingConfigEntity;
import com.aiassistant.userbusiness.models.request.RegisterBusinessRequest;
import com.aiassistant.userbusiness.models.request.UpdateBusinessProfileRequest;
import com.aiassistant.userbusiness.models.response.BusinessLookupResponse;
import com.aiassistant.userbusiness.models.response.BusinessResponse;
import com.aiassistant.userbusiness.models.response.DemoTimeResponse;
import com.aiassistant.userbusiness.models.response.ExistsResponse;
import com.aiassistant.userbusiness.repository.BusinessPhoneNumberRepository;
import com.aiassistant.userbusiness.repository.BusinessRepository;
import com.aiassistant.userbusiness.repository.ProviderPhoneNumberRepository;
import com.aiassistant.userbusiness.repository.RatingConfigRepository;
import com.aiassistant.userbusiness.services.mapper.BusinessMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BusinessService {

    private static final Logger log = LoggerFactory.getLogger(BusinessService.class);

    private static final Map<String, Integer> DEFAULT_RATING_CONFIG = Map.of(
            "LONG_CALL", 2,
            "POSITIVE_FEEDBACK", 2,
            "CALLBACK_REQUESTED", 3,
            "NEGATIVE_FEEDBACK", -1,
            "SHORT_CALL", -2,
            "AI_COULD_NOT_ANSWER", 1
    );

    private final BusinessRepository businessRepository;
    private final BusinessPhoneNumberRepository phoneNumberRepository;
    private final ProviderPhoneNumberRepository providerPhoneNumberRepository;
    private final RatingConfigRepository ratingConfigRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public BusinessResponse register(RegisterBusinessRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        if (businessRepository.existsByEmail(email)) {
            throw new ConflictException("A business with email '" + email + "' already exists");
        }

        BusinessEntity entity = BusinessEntity.builder()
                .name(request.getName().trim())
                .email(email)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .category(request.getCategory())
                .description(request.getDescription())
                .location(request.getLocation())
                .operatingHours(request.getOperatingHours())
                .whatsappNumber(request.getWhatsappNumber())
                .isActive(true)
                .build();

        BusinessEntity saved = businessRepository.save(entity);
        seedDefaultRatingConfig(saved.getId());

        log.info("Business registered id={} email={}", saved.getId(), saved.getEmail());
        return BusinessMapper.toResponse(saved);
    }

    public BusinessResponse getProfile(String businessId) {
        return BusinessMapper.toResponse(loadBusiness(businessId));
    }

    @Transactional
    public BusinessResponse updateProfile(String businessId, UpdateBusinessProfileRequest request) {
        BusinessEntity entity = loadBusiness(businessId);
        if (request.getName() != null) {
            entity.setName(request.getName().trim());
        }
        if (request.getCategory() != null) {
            entity.setCategory(request.getCategory());
        }
        if (request.getDescription() != null) {
            entity.setDescription(request.getDescription());
        }
        if (request.getLocation() != null) {
            entity.setLocation(request.getLocation());
        }
        if (request.getOperatingHours() != null) {
            entity.setOperatingHours(request.getOperatingHours());
        }
        if (request.getWhatsappNumber() != null) {
            entity.setWhatsappNumber(request.getWhatsappNumber());
        }
        BusinessEntity saved = businessRepository.save(entity);
        log.info("Business profile updated id={}", saved.getId());
        return BusinessMapper.toResponse(saved);
    }

    public BusinessLookupResponse lookupByPhoneNumber(String phoneNumber) {
        ProviderPhoneNumberEntity providerNumber = providerPhoneNumberRepository
                .findByPhoneNumberAndStatus(phoneNumber, "assigned")
                .orElseThrow(() -> new BusinessNotFoundException(
                        "No business found for phone number: " + phoneNumber));
        BusinessPhoneNumberEntity link = phoneNumberRepository
                .findByProviderPhoneNumberId(providerNumber.getId())
                .orElseThrow(() -> new BusinessNotFoundException(
                        "No business found for phone number: " + phoneNumber));
        BusinessEntity business = loadBusiness(link.getBusinessId());
        return BusinessLookupResponse.builder()
                .businessId(business.getId())
                .name(business.getName())
                .phoneNumber(providerNumber.getPhoneNumber())
                .isActive(business.getIsActive())
                .build();
    }

    public ExistsResponse exists(String businessId) {
        boolean exists = businessRepository.existsById(businessId);
        return ExistsResponse.builder().id(businessId).exists(exists).build();
    }

    public DemoTimeResponse getDemoTime(String businessId) {
        BusinessEntity entity = loadBusiness(businessId);
        return DemoTimeResponse.builder()
                .businessId(entity.getId())
                .secondsRemaining(entity.getLiveDemoSecondsRemaining())
                .build();
    }

    @Transactional
    public DemoTimeResponse decrementDemoTime(String businessId, int seconds) {
        if (seconds <= 0) {
            throw new AppException("Decrement seconds must be positive");
        }
        int updated = businessRepository.decrementDemoTime(businessId, seconds);
        if (updated == 0) {
            BusinessEntity entity = businessRepository.findById(businessId).orElse(null);
            if (entity == null) {
                throw new BusinessNotFoundException("Business not found: " + businessId);
            }
            log.info("Demo time already exhausted businessId={} remaining={}s",
                    businessId, entity.getLiveDemoSecondsRemaining());
        }
        BusinessEntity entity = loadBusiness(businessId);
        log.info("Demo time decremented businessId={} by={}s remaining={}s",
                businessId, seconds, entity.getLiveDemoSecondsRemaining());
        return DemoTimeResponse.builder()
                .businessId(entity.getId())
                .secondsRemaining(entity.getLiveDemoSecondsRemaining())
                .build();
    }

    BusinessEntity loadBusiness(String businessId) {
        return businessRepository.findById(businessId)
                .orElseThrow(() -> new BusinessNotFoundException(
                        "Business not found: " + businessId));
    }

    private void seedDefaultRatingConfig(String businessId) {
        List<RatingConfigEntity> defaults = DEFAULT_RATING_CONFIG.entrySet().stream()
                .map(entry -> RatingConfigEntity.builder()
                        .businessId(businessId)
                        .signalKey(entry.getKey())
                        .scoreValue(entry.getValue())
                        .build())
                .toList();
        ratingConfigRepository.saveAll(defaults);
        log.debug("Seeded {} default rating config rows for business {}", defaults.size(), businessId);
    }
}
