package com.aiassistant.summary.services;

import com.aiassistant.summary.clients.CallOrchestrationClient;
import com.aiassistant.summary.clients.UserBusinessLeadClient;
import com.aiassistant.summary.configuration.ServiceConfiguration;
import com.aiassistant.summary.models.request.CreateLeadRequest;
import com.aiassistant.summary.llm.LlmMessage;
import com.aiassistant.summary.llm.LlmProvider;
import com.aiassistant.summary.llm.LlmProviderRegistry;
import com.aiassistant.summary.llm.LlmReply;
import com.aiassistant.summary.llm.LlmRequest;
import com.aiassistant.summary.llm.TokenUsage;
import com.aiassistant.summary.models.dao.CallSummaryEntity;
import com.aiassistant.summary.models.response.TranscriptPayload;
import com.aiassistant.summary.repository.CallSummaryRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Trigger → fetch → LLM → persist pipeline for post-call summaries.
 *
 * <p>The controller calls {@link #scheduleSummary(String)} which returns
 * immediately; the actual work runs on the {@code summaryExecutor} pool.
 * Idempotency is enforced via a {@code call_log_id UNIQUE} constraint on
 * {@code call_summaries} plus a pre-check, so duplicate triggers (retries,
 * re-deliveries) at-most-once produce a row.
 */
@Service
@RequiredArgsConstructor
public class SummaryService {

    private static final Logger log = LoggerFactory.getLogger(SummaryService.class);

    private static final String PROMPT_PATH = "prompts/summary-system-prompt.txt";

    private String systemPrompt;

    @PostConstruct
    void loadPrompt() {
        try {
            systemPrompt = new ClassPathResource(PROMPT_PATH)
                    .getContentAsString(StandardCharsets.UTF_8);
            log.info("[summary] loaded system prompt from classpath:{} ({} chars)",
                    PROMPT_PATH, systemPrompt.length());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load summary system prompt from " + PROMPT_PATH, e);
        }
    }

    private final LlmProviderRegistry providerRegistry;
    private final ServiceConfiguration serviceConfiguration;
    private final CallOrchestrationClient callOrchClient;
    private final UserBusinessLeadClient leadClient;
    private final CallSummaryRepository repository;
    private final ObjectMapper mapper;

    /**
     * Controller-facing entry point. Returns immediately (202 semantics) —
     * the heavy work is dispatched to the summary executor. Caller does
     * not learn the LLM result; it lands in {@code call_summaries}.
     */
    @Async("summaryExecutor")
    public void scheduleSummary(String callLogId) {
        try {
            if (repository.existsByCallLogId(callLogId)) {
                log.info("[summary] skip — already summarised callLogId={}", callLogId);
                return;
            }

            TranscriptPayload payload = callOrchClient.fetchTranscript(callLogId);
            if (payload.getHistory() == null || payload.getHistory().isEmpty()) {
                log.warn("[summary] empty transcript — skipping LLM callLogId={}", callLogId);
                return;
            }

            Result result = runLlmAndBuildEntity(callLogId, payload);
            persist(result.entity);
            log.info("[summary] persisted callLogId={} interest={} queryType={} callback={}",
                    callLogId, result.entity.getInterestRating(), result.entity.getQueryType(),
                    result.entity.getCallbackNeeded());

            // Lead dispatch is best-effort and isolated — a failure here
            // must not unwind the summary persist above. user-business-service
            // applies the per-business interest threshold and may drop the
            // candidate; that's intentional, not an error.
            if (result.lead != null) {
                try {
                    leadClient.createLead(result.lead);
                } catch (Exception leadEx) {
                    log.error("[summary] lead dispatch failed callLogId={}: {}",
                            callLogId, leadEx.getMessage());
                }
            }
        } catch (Exception ex) {
            log.error("[summary] FAILED callLogId={}: {}", callLogId, ex.getMessage(), ex);
        }
    }

    @Transactional
    void persist(CallSummaryEntity entity) {
        // Race guard: another thread may have inserted between existsByCallLogId
        // and persist. Re-check inside the txn boundary; on conflict we no-op.
        if (repository.existsByCallLogId(entity.getCallLogId())) {
            log.info("[summary] race-skip on persist callLogId={}", entity.getCallLogId());
            return;
        }
        repository.save(entity);
    }

    /** Carries the summary row plus an optional lead candidate so we can
     *  persist + dispatch from the same place without re-running the LLM. */
    private record Result(CallSummaryEntity entity, CreateLeadRequest lead) {}

    private Result runLlmAndBuildEntity(String callLogId, TranscriptPayload payload) {
        LlmProvider provider = providerRegistry.get(null);
        ServiceConfiguration.Summary cfg = serviceConfiguration.getSummary();

        LlmRequest llmReq = LlmRequest.builder()
                .systemPrompt(systemPrompt)
                .messages(List.of(LlmMessage.builder()
                        .role(LlmMessage.Role.USER)
                        .content(buildUserPrompt(payload))
                        .build()))
                .maxOutputTokens(cfg.getMaxOutputTokens())
                .temperature(cfg.getTemperature())
                .cacheSystemPrompt(false)
                .build();

        LlmReply reply = provider.complete(llmReq);
        String raw = reply.getText() == null ? "" : reply.getText().trim();
        log.info("[summary] llm provider={} chars={} callLogId={}",
                provider.id(), raw.length(), callLogId);

        ParsedSummary parsed = parse(raw);
        TokenUsage usage = reply.getUsage();
        CallSummaryEntity entity = CallSummaryEntity.builder()
                .callLogId(callLogId)
                .businessId(payload.getBusinessId())
                .callerName(parsed.callerName)
                .customerPhone(payload.getCustomerPhone())
                .queryType(parsed.queryType)
                .interestRating(parsed.interestRating)
                .interestReason(parsed.interestReason)
                .mainConcerns(parsed.mainConcerns)
                .callbackNeeded(Boolean.TRUE.equals(parsed.callbackNeeded))
                .callbackReason(parsed.callbackReason)
                .unansweredQuestions(parsed.unansweredQuestions)
                .summaryText(parsed.summary)
                .provider(provider.id())
                .inputTokens(usage == null ? null : (int) usage.getInputTokens())
                .outputTokens(usage == null ? null : (int) usage.getOutputTokens())
                .totalTokens(usage == null ? null
                        : (int) (usage.getInputTokens() + usage.getOutputTokens()))
                .build();

        CreateLeadRequest lead = buildLeadCandidate(callLogId, payload, parsed);
        return new Result(entity, lead);
    }

    /**
     * Decide whether to forward a lead to user-business-service and shape
     * the payload. The explicit-type cases (appointment, human-request) are
     * always forwarded; the HIGH_INTEREST case sends {@code leadType=null}
     * and lets user-business-service apply the per-business threshold so
     * the owner-tuned config wins.
     *
     * <p>Returns {@code null} when the call has no actionable signal at all —
     * spares user-business-service a no-op round trip.</p>
     */
    private CreateLeadRequest buildLeadCandidate(String callLogId, TranscriptPayload payload,
                                                 ParsedSummary parsed) {
        ParsedLeadIntent intent = parsed.leadIntent;
        boolean hasInterest = parsed.interestRating != null;
        if (intent == null && !hasInterest) return null;
        if (intent != null && !intent.isAppointment && !intent.isHumanRequest && !hasInterest) {
            return null;
        }
        String leadType = intent == null ? null
                : intent.isAppointment ? "APPOINTMENT"
                : intent.isHumanRequest ? "HUMAN_REQUEST"
                : null;
        // If neither explicit type nor numeric interest is present, there's
        // nothing to forward.
        if (leadType == null && !hasInterest) return null;

        return CreateLeadRequest.builder()
                .businessId(payload.getBusinessId())
                .callLogId(callLogId)
                .leadType(leadType)
                .customerPhone(payload.getCustomerPhone())
                .customerName(parsed.callerName)
                .callerLanguage(payload.getLanguage())
                .summary(parsed.summary)
                .interestRating(parsed.interestRating)
                .service(intent == null ? null : intent.service)
                .preferredWindowRaw(intent == null ? null : intent.preferredWindowRaw)
                .structuredSlots(intent == null ? null : intent.structuredSlots)
                .suggestedDatetime(intent == null ? null : intent.suggestedDatetime)
                .build();
    }

    private String buildUserPrompt(TranscriptPayload p) {
        StringBuilder sb = new StringBuilder();
        // Today's date is needed so the LLM can resolve "tomorrow" / "next
        // Monday" / "23rd" in the transcript to a concrete YYYY-MM-DD.
        sb.append("Today's date (IST): ")
          .append(java.time.LocalDate.now(java.time.ZoneId.of("Asia/Kolkata")))
          .append('\n')
          .append("Caller timezone: Asia/Kolkata (IST, UTC+5:30). All times the caller mentions are in IST.\n");
        if (p.getBusinessName() != null && !p.getBusinessName().isBlank()) {
            sb.append("Business: ").append(p.getBusinessName()).append('\n');
        }
        if (p.getCustomerPhone() != null) {
            sb.append("Caller phone: ").append(p.getCustomerPhone()).append('\n');
        }
        if (p.getLanguage() != null) {
            sb.append("Detected language: ").append(p.getLanguage()).append('\n');
        }
        if (p.getKnowledge() != null && !p.getKnowledge().isBlank()) {
            sb.append("\n--- BUSINESS KNOWLEDGE (what the assistant was told) ---\n")
              .append(p.getKnowledge()).append('\n')
              .append("--- END KNOWLEDGE ---\n");
        }
        sb.append("\n--- TRANSCRIPT ---\n");
        if (p.getHistory() != null) {
            for (Map<String, String> turn : p.getHistory()) {
                String role = turn.getOrDefault("role", "?").toUpperCase();
                String content = turn.getOrDefault("content", "");
                sb.append(role).append(": ").append(content).append('\n');
            }
        }
        sb.append("--- END TRANSCRIPT ---\n\nProduce the JSON summary now.");
        return sb.toString();
    }

    private ParsedSummary parse(String raw) {
        String json = extractJson(raw);
        ParsedSummary p = new ParsedSummary();
        try {
            JsonNode node = mapper.readTree(json);
            p.summary = text(node, "summary");
            p.callerName = text(node, "callerName");
            p.queryType = text(node, "queryType");
            p.interestRating = decimalVal(node, "interestRating");
            p.interestReason = text(node, "interestReason");
            p.mainConcerns = stringList(node, "mainConcerns");
            p.callbackNeeded = boolVal(node, "callbackNeeded");
            p.callbackReason = text(node, "callbackReason");
            p.unansweredQuestions = stringList(node, "unansweredQuestions");
            p.leadIntent = parseLeadIntent(node.get("leadIntent"));
        } catch (Exception e) {
            log.warn("[summary] JSON parse failed — saving raw text only. err={} preview=\"{}\"",
                    e.getMessage(), preview(raw));
            p.summary = raw;
        }
        return p;
    }

    private static String extractJson(String s) {
        if (s == null) return "{}";
        String t = s.trim();
        if (t.startsWith("```")) {
            int firstNl = t.indexOf('\n');
            if (firstNl > 0) t = t.substring(firstNl + 1);
            int closeFence = t.lastIndexOf("```");
            if (closeFence > 0) t = t.substring(0, closeFence);
            t = t.trim();
        }
        int open = t.indexOf('{');
        int close = t.lastIndexOf('}');
        if (open >= 0 && close > open) return t.substring(open, close + 1);
        return t;
    }

    private static String text(JsonNode n, String f) {
        JsonNode v = n.get(f);
        if (v == null || v.isNull()) return null;
        String s = v.isTextual() ? v.asText() : v.toString();
        return s.isBlank() ? null : s;
    }

    private static Integer intVal(JsonNode n, String f) {
        JsonNode v = n.get(f);
        if (v == null || v.isNull()) return null;
        if (v.isInt() || v.isLong()) return v.asInt();
        try { return Integer.parseInt(v.asText().trim()); }
        catch (Exception ignored) { return null; }
    }

    private static java.math.BigDecimal decimalVal(JsonNode n, String f) {
        JsonNode v = n.get(f);
        if (v == null || v.isNull()) return null;
        try {
            java.math.BigDecimal raw = v.isNumber()
                    ? v.decimalValue()
                    : new java.math.BigDecimal(v.asText().trim());
            // Clamp to 0.0–10.0 with one decimal place so a hallucinated 12.5
            // doesn't fail the column constraint downstream.
            if (raw.compareTo(java.math.BigDecimal.ZERO) < 0) raw = java.math.BigDecimal.ZERO;
            if (raw.compareTo(java.math.BigDecimal.TEN) > 0) raw = java.math.BigDecimal.TEN;
            return raw.setScale(1, java.math.RoundingMode.HALF_UP);
        } catch (Exception ignored) { return null; }
    }

    private static java.time.Instant instantVal(JsonNode n, String f) {
        JsonNode v = n.get(f);
        if (v == null || v.isNull()) return null;
        try { return java.time.Instant.parse(v.asText().trim()); }
        catch (Exception ignored) { return null; }
    }

    private static Boolean boolVal(JsonNode n, String f) {
        JsonNode v = n.get(f);
        if (v == null || v.isNull()) return null;
        if (v.isBoolean()) return v.asBoolean();
        String s = v.asText().trim().toLowerCase();
        if (s.equals("true") || s.equals("yes")) return true;
        if (s.equals("false") || s.equals("no")) return false;
        return null;
    }

    private static List<String> stringList(JsonNode n, String f) {
        JsonNode v = n.get(f);
        if (v == null || v.isNull() || !v.isArray()) return List.of();
        List<String> out = new ArrayList<>(v.size());
        v.forEach(item -> {
            if (item == null || item.isNull()) return;
            String s = item.isTextual() ? item.asText() : item.toString();
            if (!s.isBlank()) out.add(s);
        });
        return out;
    }

    private static String preview(String s) {
        if (s == null) return "";
        return s.length() <= 200 ? s : s.substring(0, 200) + "…";
    }

    private ParsedLeadIntent parseLeadIntent(JsonNode node) {
        if (node == null || node.isNull() || !node.isObject()) return null;
        ParsedLeadIntent l = new ParsedLeadIntent();
        l.isAppointment = Boolean.TRUE.equals(boolVal(node, "isAppointment"));
        l.isHumanRequest = Boolean.TRUE.equals(boolVal(node, "isHumanRequest"));
        l.service = text(node, "service");
        l.preferredWindowRaw = text(node, "preferredWindowRaw");
        l.notes = text(node, "notes");
        l.suggestedDatetime = instantVal(node, "suggestedDatetime");
        JsonNode slots = node.get("structuredSlots");
        if (slots != null && slots.isArray()) {
            List<Map<String, Object>> out = new ArrayList<>(slots.size());
            for (JsonNode slot : slots) {
                if (slot == null || slot.isNull() || !slot.isObject()) continue;
                Map<String, Object> m = new java.util.LinkedHashMap<>();
                JsonNode date = slot.get("date");
                if (date != null && !date.isNull()) m.put("date", date.asText());
                JsonNode period = slot.get("period");
                if (period != null && !period.isNull()) m.put("period", period.asText());
                if (!m.isEmpty()) out.add(m);
            }
            l.structuredSlots = out;
        }
        return l;
    }

    /** Tiny POJO so the parser can hand fields back without 9 method args. */
    private static final class ParsedSummary {
        String summary;
        String callerName;
        String queryType;
        java.math.BigDecimal interestRating;
        String interestReason;
        List<String> mainConcerns;
        Boolean callbackNeeded;
        String callbackReason;
        List<String> unansweredQuestions;
        ParsedLeadIntent leadIntent;
    }

    private static final class ParsedLeadIntent {
        boolean isAppointment;
        boolean isHumanRequest;
        String service;
        String preferredWindowRaw;
        List<Map<String, Object>> structuredSlots;
        java.time.Instant suggestedDatetime;
        String notes;
    }
}
