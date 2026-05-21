package com.aiassistant.knowledge.repository;

import com.aiassistant.knowledge.models.dao.BusinessFreeformEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BusinessFreeformRepository extends JpaRepository<BusinessFreeformEntity, String> {
    Optional<BusinessFreeformEntity> findByBusinessId(String businessId);
}
