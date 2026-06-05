package com.aiassistant.callorchestration.repository;

import com.aiassistant.callorchestration.models.dao.CallLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CallLogRepository extends JpaRepository<CallLogEntity, String> {

    List<CallLogEntity> findByBusinessIdOrderByCallStartedAtDesc(String businessId);

    List<CallLogEntity> findByBusinessIdAndCallbackRequestedTrueOrderByCallStartedAtAsc(String businessId);

    Optional<CallLogEntity> findByIdAndBusinessId(String id, String businessId);

    Optional<CallLogEntity> findByProviderCallId(String providerCallId);

    List<CallLogEntity> findByIdInAndBusinessId(List<String> ids, String businessId);
}
