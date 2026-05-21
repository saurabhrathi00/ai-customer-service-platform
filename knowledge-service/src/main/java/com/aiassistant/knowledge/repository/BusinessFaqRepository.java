package com.aiassistant.knowledge.repository;

import com.aiassistant.knowledge.models.dao.BusinessFaqEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BusinessFaqRepository extends JpaRepository<BusinessFaqEntity, String> {

    List<BusinessFaqEntity> findAllByBusinessIdOrderByPriorityDescCreatedAtAsc(String businessId);

    List<BusinessFaqEntity> findAllByBusinessIdAndIsActiveOrderByPriorityDescCreatedAtAsc(
            String businessId, boolean isActive);

    Optional<BusinessFaqEntity> findByIdAndBusinessId(String id, String businessId);

    long countByBusinessIdAndIsActive(String businessId, boolean isActive);
}
