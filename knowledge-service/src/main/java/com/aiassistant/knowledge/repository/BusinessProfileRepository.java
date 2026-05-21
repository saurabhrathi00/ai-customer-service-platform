package com.aiassistant.knowledge.repository;

import com.aiassistant.knowledge.models.dao.BusinessProfileEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BusinessProfileRepository extends JpaRepository<BusinessProfileEntity, String> {
    Optional<BusinessProfileEntity> findByBusinessId(String businessId);
    boolean existsByBusinessId(String businessId);
}
