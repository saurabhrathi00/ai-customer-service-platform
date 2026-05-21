package com.aiassistant.knowledge.repository;

import com.aiassistant.knowledge.models.dao.BusinessEscalationRuleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BusinessEscalationRuleRepository extends JpaRepository<BusinessEscalationRuleEntity, String> {

    List<BusinessEscalationRuleEntity> findAllByBusinessIdOrderByCreatedAtAsc(String businessId);

    List<BusinessEscalationRuleEntity> findAllByBusinessIdAndIsActiveOrderByCreatedAtAsc(
            String businessId, boolean isActive);

    Optional<BusinessEscalationRuleEntity> findByIdAndBusinessId(String id, String businessId);
}
