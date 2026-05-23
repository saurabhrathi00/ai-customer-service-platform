package com.aiassistant.summary.repository;

import com.aiassistant.summary.models.dao.CallSummaryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CallSummaryRepository extends JpaRepository<CallSummaryEntity, String> {

    /** First-wins idempotency guard. Two triggers for the same callLogId
     *  (e.g. retry race) will see this and skip the second LLM call. */
    Optional<CallSummaryEntity> findByCallLogId(String callLogId);

    boolean existsByCallLogId(String callLogId);
}
