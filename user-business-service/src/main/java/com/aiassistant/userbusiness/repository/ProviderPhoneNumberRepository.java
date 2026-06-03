package com.aiassistant.userbusiness.repository;

import com.aiassistant.userbusiness.models.dao.ProviderPhoneNumberEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProviderPhoneNumberRepository extends JpaRepository<ProviderPhoneNumberEntity, String> {

    Optional<ProviderPhoneNumberEntity> findByPhoneNumber(String phoneNumber);

    Optional<ProviderPhoneNumberEntity> findByPhoneNumberAndStatus(String phoneNumber, String status);

    List<ProviderPhoneNumberEntity> findAllByProviderId(String providerId);

    boolean existsByPhoneNumber(String phoneNumber);
}
