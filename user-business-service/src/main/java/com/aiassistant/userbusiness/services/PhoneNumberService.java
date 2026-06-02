package com.aiassistant.userbusiness.services;

import com.aiassistant.userbusiness.exceptions.BusinessNotFoundException;
import com.aiassistant.userbusiness.exceptions.ConflictException;
import com.aiassistant.userbusiness.models.dao.BusinessPhoneNumberEntity;
import com.aiassistant.userbusiness.models.request.AddPhoneNumberRequest;
import com.aiassistant.userbusiness.models.response.PhoneNumberResponse;
import com.aiassistant.userbusiness.repository.BusinessPhoneNumberRepository;
import com.aiassistant.userbusiness.repository.BusinessRepository;
import com.aiassistant.userbusiness.services.mapper.BusinessMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PhoneNumberService {

    private static final Logger log = LoggerFactory.getLogger(PhoneNumberService.class);

    private final BusinessPhoneNumberRepository phoneNumberRepository;
    private final BusinessRepository businessRepository;

    public List<PhoneNumberResponse> list(String businessId) {
        assertBusinessExists(businessId);
        return phoneNumberRepository.findAllByBusinessId(businessId).stream()
                .map(BusinessMapper::toResponse)
                .toList();
    }

    @Transactional
    public PhoneNumberResponse add(String businessId, AddPhoneNumberRequest request) {
        assertBusinessExists(businessId);
        String phoneNumber = request.getTwilioNumber();
        if (phoneNumber != null && phoneNumber.startsWith("+")) {
            phoneNumber = phoneNumber.substring(1);
        }
        if (phoneNumberRepository.existsByTwilioNumber(phoneNumber)) {
            throw new ConflictException("Phone number already assigned: " + phoneNumber);
        }
        BusinessPhoneNumberEntity entity = BusinessPhoneNumberEntity.builder()
                .businessId(businessId)
                .twilioNumber(phoneNumber)
                .label(request.getLabel())
                .isActive(true)
                .build();
        BusinessPhoneNumberEntity saved = phoneNumberRepository.save(entity);
        log.info("Phone number added businessId={} twilioNumber={}", businessId, saved.getTwilioNumber());
        return BusinessMapper.toResponse(saved);
    }

    @Transactional
    public void delete(String businessId, String phoneNumberId) {
        BusinessPhoneNumberEntity entity = phoneNumberRepository
                .findByIdAndBusinessId(phoneNumberId, businessId)
                .orElseThrow(() -> new BusinessNotFoundException(
                        "Phone number " + phoneNumberId + " not found for business " + businessId));
        phoneNumberRepository.delete(entity);
        log.info("Phone number deleted businessId={} phoneId={}", businessId, phoneNumberId);
    }

    private void assertBusinessExists(String businessId) {
        if (!businessRepository.existsById(businessId)) {
            throw new BusinessNotFoundException("Business not found: " + businessId);
        }
    }
}
