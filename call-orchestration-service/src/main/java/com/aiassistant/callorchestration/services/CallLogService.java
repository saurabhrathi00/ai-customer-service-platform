package com.aiassistant.callorchestration.services;

import com.aiassistant.callorchestration.exceptions.AppException;
import com.aiassistant.callorchestration.exceptions.CallNotFoundException;
import com.aiassistant.callorchestration.models.dao.CallLogEntity;
import com.aiassistant.callorchestration.models.request.UpdateFeedbackRequest;
import com.aiassistant.callorchestration.models.request.UpdateSummaryRequest;
import com.aiassistant.callorchestration.models.response.CallLogResponse;
import com.aiassistant.callorchestration.models.response.CallbackResponse;
import com.aiassistant.callorchestration.models.response.TranscriptPayload;
import com.fasterxml.jackson.core.type.TypeReference;
import com.aiassistant.callorchestration.repository.CallLogRepository;
import com.aiassistant.callorchestration.services.mapper.CallLogMapper;
import com.aiassistant.callorchestration.telephony.CallSession;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CallLogService {

    private static final Logger log = LoggerFactory.getLogger(CallLogService.class);

    private final CallLogRepository callLogRepository;
    private final ObjectMapper objectMapper;

    public List<CallLogResponse> listRecent(String businessId) {
        return callLogRepository.findByBusinessIdOrderByCallStartedAtDesc(businessId)
                .stream().map(CallLogMapper::toResponse).toList();
    }

    public List<CallbackResponse> listCallbacks(String businessId) {
        return callLogRepository.findByBusinessIdAndCallbackRequestedTrueOrderByCallStartedAtAsc(businessId)
                .stream().map(CallLogMapper::toCallbackResponse).toList();
    }

    @Transactional
    public void delete(String callId, String businessId) {
        CallLogEntity entity = callLogRepository.findByIdAndBusinessId(callId, businessId)
                .orElseThrow(() -> new CallNotFoundException("Call not found: " + callId));
        callLogRepository.delete(entity);
        log.info("Deleted call callId={} businessId={}", callId, businessId);
    }

    @Transactional
    public int deleteBulk(List<String> callIds, String businessId) {
        List<CallLogEntity> entities = callLogRepository.findByIdInAndBusinessId(callIds, businessId);
        if (entities.isEmpty()) return 0;
        callLogRepository.deleteAll(entities);
        log.info("Bulk deleted {} calls businessId={}", entities.size(), businessId);
        return entities.size();
    }

    @Transactional
    public CallLogResponse updateFeedback(String callId, UpdateFeedbackRequest request) {
        CallLogEntity entity = callLogRepository.findByIdAndBusinessId(callId, request.getBusinessId())
                .orElseThrow(() -> new CallNotFoundException("Call not found: " + callId));
        entity.setFeedbackScore(request.getFeedbackScore());
        CallLogEntity saved = callLogRepository.save(entity);
        log.info("Updated feedback callId={} score={}", callId, request.getFeedbackScore());
        return CallLogMapper.toResponse(saved);
    }

    @Transactional
    public CallLogResponse updateSummary(String callId, UpdateSummaryRequest request) {
        CallLogEntity entity = callLogRepository.findByIdAndBusinessId(callId, request.getBusinessId())
                .orElseThrow(() -> new CallNotFoundException("Call not found: " + callId));
        if (request.getCallSummary() != null) entity.setCallSummary(request.getCallSummary());
        if (request.getQueryType() != null) entity.setQueryType(request.getQueryType());
        if (request.getInterestRating() != null) entity.setInterestRating(request.getInterestRating());
        CallLogEntity saved = callLogRepository.save(entity);
        log.info("Updated summary callId={}", callId);
        return CallLogMapper.toResponse(saved);
    }

    /**
     * Return the stored transcript + caller context for a call. Used by
     * conversation-summary-service to fetch the data it needs after a
     * lightweight trigger. {@code businessName} and {@code knowledge} are
     * not persisted on the row (yet) and come back null.
     */
    public TranscriptPayload getTranscript(String callLogId) {
        CallLogEntity entity = callLogRepository.findById(callLogId)
                .orElseThrow(() -> new CallNotFoundException("Call log not found: " + callLogId));
        List<Map<String, String>> history = deserialiseHistory(entity.getTranscript());
        return TranscriptPayload.builder()
                .callLogId(entity.getId())
                .businessId(entity.getBusinessId())
                .customerPhone(entity.getCustomerPhone())
                .history(history)
                .build();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, String>> deserialiseHistory(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json,
                    new TypeReference<List<Map<String, String>>>() {});
        } catch (Exception ex) {
            log.warn("Failed to deserialise transcript JSON: {}", ex.getMessage());
            return List.of();
        }
    }

    /**
     * Persist the call_logs row using the in-memory {@link CallSession} state.
     * The conversation history (set on the session at call end) is serialised
     * to JSON and stored in the transcript column.
     */
    @Transactional
    public CallLogEntity persistOnDisconnect(CallSession session, Instant endedAt) {
        log.info("Persisting call log for callId={} business={}", session.getCallId(), session.getBusinessId());

        Integer durationSecs = null;
        if (session.getStartedAt() != null && endedAt != null) {
            durationSecs = (int) Duration.between(session.getStartedAt(), endedAt).toSeconds();
        }

        CallLogEntity entity = CallLogEntity.builder()
                .businessId(session.getBusinessId())
                .customerPhone(session.getCustomerPhone())
                .provider(session.getProvider())
                .providerCallId(session.getCallId())
                .callbackRequested(session.isCallbackRequested())
                .feedbackScore(session.getFeedbackScore())
                .callStartedAt(session.getStartedAt())
                .callEndedAt(endedAt)
                .callDurationSecs(durationSecs)
                .transcript(serialiseHistory(session))
                .build();
        return callLogRepository.save(entity);
    }

    private String serialiseHistory(CallSession session) {
        if (session.getHistory() == null || session.getHistory().isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(session.getHistory());
        } catch (JsonProcessingException ex) {
            throw new AppException("Failed to serialise conversation history: " + ex.getMessage());
        }
    }
}
