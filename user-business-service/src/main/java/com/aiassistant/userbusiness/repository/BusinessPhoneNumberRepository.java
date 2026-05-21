package com.aiassistant.userbusiness.repository;

import com.aiassistant.userbusiness.models.dao.BusinessPhoneNumberEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BusinessPhoneNumberRepository extends JpaRepository<BusinessPhoneNumberEntity, String> {

    List<BusinessPhoneNumberEntity> findAllByBusinessId(String businessId);

    Optional<BusinessPhoneNumberEntity> findByTwilioNumber(String twilioNumber);

    Optional<BusinessPhoneNumberEntity> findByIdAndBusinessId(String id, String businessId);

    boolean existsByTwilioNumber(String twilioNumber);
}