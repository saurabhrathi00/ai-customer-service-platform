package com.aiassistant.userbusiness.services;

import com.aiassistant.userbusiness.exceptions.BusinessNotFoundException;
import com.aiassistant.userbusiness.exceptions.ConflictException;
import com.aiassistant.userbusiness.models.dao.BusinessPhoneNumberEntity;
import com.aiassistant.userbusiness.models.dao.ProviderPhoneNumberEntity;
import com.aiassistant.userbusiness.models.dao.TelephonyProviderEntity;
import com.aiassistant.userbusiness.models.request.AddPhoneNumberRequest;
import com.aiassistant.userbusiness.models.response.PhoneNumberResponse;
import com.aiassistant.userbusiness.repository.BusinessPhoneNumberRepository;
import com.aiassistant.userbusiness.repository.BusinessRepository;
import com.aiassistant.userbusiness.repository.ProviderPhoneNumberRepository;
import com.aiassistant.userbusiness.repository.TelephonyProviderRepository;
import com.aiassistant.userbusiness.services.mapper.BusinessMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PhoneNumberService {

    private static final Logger log = LoggerFactory.getLogger(PhoneNumberService.class);

    private final BusinessPhoneNumberRepository phoneNumberRepository;
    private final ProviderPhoneNumberRepository providerPhoneNumberRepository;
    private final BusinessRepository businessRepository;
    private final TelephonyProviderRepository providerRepository;

    public List<PhoneNumberResponse> list(String businessId) {
        assertBusinessExists(businessId);
        List<BusinessPhoneNumberEntity> links = phoneNumberRepository.findAllByBusinessId(businessId);
        Map<String, String> providerSlugs = providerRepository.findAll().stream()
                .collect(Collectors.toMap(TelephonyProviderEntity::getId, TelephonyProviderEntity::getSlug));
        return links.stream()
                .map(link -> {
                    ProviderPhoneNumberEntity providerNumber = providerPhoneNumberRepository
                            .findById(link.getProviderPhoneNumberId())
                            .orElseThrow();
                    return BusinessMapper.toResponse(link, providerNumber,
                            providerSlugs.get(providerNumber.getProviderId()));
                })
                .toList();
    }

    @Transactional
    public PhoneNumberResponse add(String businessId, AddPhoneNumberRequest request) {
        assertBusinessExists(businessId);
        String raw = request.getPhoneNumber();
        if (raw != null && raw.startsWith("+")) {
            raw = raw.substring(1);
        }
        final String phoneNumber = raw;

        ProviderPhoneNumberEntity providerNumber = providerPhoneNumberRepository
                .findByPhoneNumber(phoneNumber)
                .orElseThrow(() -> new BusinessNotFoundException(
                        "Phone number not found in any provider inventory: " + phoneNumber));

        if ("assigned".equals(providerNumber.getStatus())) {
            throw new ConflictException("Phone number already registered: " + phoneNumber);
        }

        TelephonyProviderEntity provider = providerRepository.findById(providerNumber.getProviderId())
                .orElseThrow(() -> new BusinessNotFoundException(
                        "Provider not found for phone number: " + phoneNumber));

        providerNumber.setStatus("assigned");
        providerNumber = providerPhoneNumberRepository.save(providerNumber);

        BusinessPhoneNumberEntity link = BusinessPhoneNumberEntity.builder()
                .businessId(businessId)
                .providerPhoneNumberId(providerNumber.getId())
                .label(request.getLabel())
                .build();
        link = phoneNumberRepository.save(link);

        log.info("Phone number added businessId={} phoneNumber={} provider={}",
                businessId, providerNumber.getPhoneNumber(), provider.getSlug());
        return BusinessMapper.toResponse(link, providerNumber, provider.getSlug());
    }

    @Transactional
    public void delete(String businessId, String phoneNumberId) {
        BusinessPhoneNumberEntity link = phoneNumberRepository
                .findByIdAndBusinessId(phoneNumberId, businessId)
                .orElseThrow(() -> new BusinessNotFoundException(
                        "Phone number " + phoneNumberId + " not found for business " + businessId));

        ProviderPhoneNumberEntity providerNumber = providerPhoneNumberRepository
                .findById(link.getProviderPhoneNumberId())
                .orElseThrow();
        providerNumber.setStatus("released");
        providerPhoneNumberRepository.save(providerNumber);

        phoneNumberRepository.delete(link);
        log.info("Phone number released businessId={} phoneId={}", businessId, phoneNumberId);
    }

    private void assertBusinessExists(String businessId) {
        if (!businessRepository.existsById(businessId)) {
            throw new BusinessNotFoundException("Business not found: " + businessId);
        }
    }
}
