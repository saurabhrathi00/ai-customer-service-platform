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
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    private static final String SYSTEM_PROMPT = """
            You are a call-quality analyst. You read a phone-call transcript
            between a CUSTOMER (caller) and an ASSISTANT (an AI voice agent
            for a business) and produce a structured JSON summary used to
            populate the business's call dashboard.

            Output ONLY a single JSON object with EXACTLY these keys:
            {
              "summary": "<2-3 sentence neutral description of what happened on the call>",
              "callerName": "<the caller's name if they introduced themselves, else null>",
              "queryType": "<one of: product_inquiry | pricing | support | complaint | appointment | callback_request | other>",
              "interestRating": <number 0.0-10.0 with at most ONE decimal place. How interested the caller seemed in doing business. 0=cold/wrong-number, 5=mild interest, 7-8=warm lead, 9-10=ready to buy. Decimals are encouraged when the signal is between bands (e.g. 7.5).>,
              "interestReason": "<one short sentence justifying the rating>",
              "mainConcerns": ["<concern or question in caller's own words>", ...],
              "callbackNeeded": <true if the caller explicitly asked for a human OR the assistant could not answer a substantive question; else false>,
              "callbackReason": "<one short sentence if callbackNeeded=true, else null>",
              "unansweredQuestions": ["<question the assistant could not answer>", ...],
              "leadIntent": {
                "isAppointment": <true if the caller wanted to book / reschedule an appointment, else false>,
                "isHumanRequest": <true if the caller explicitly asked for a human OR the assistant couldn't substantively answer; else false>,
                "service": "<short service name, e.g. 'haircut', 'consultation' — null if not stated or not an appointment>",
                "preferredWindowRaw": "<verbatim from caller including date AND time, e.g. 'Wednesday at 10am', 'tomorrow around lunch' — null if neither stated>",
                "structuredSlots": [
                  { "date": "YYYY-MM-DD", "period": "MORNING|AFTERNOON|EVENING|NIGHT|ANY" }
                ],
                "suggestedDatetime": "<ISO-8601 UTC instant the owner can pre-fill in the approve form, e.g. '2026-05-28T04:30:00Z' for Wed 10:00 IST. Populate WHENEVER the caller stated both a day AND a rough time — convert as best you can (treat ambiguous times like 'morning' as 10:00, 'afternoon' as 14:00, 'evening' as 18:00). Use null ONLY when day or time is entirely missing.>",
                "notes": "<anything else useful for the owner: first-visit, special requests, urgency — null if none>"
              }
            }

            Hard rules:
            - Output ONLY the JSON object. No code fences, no preamble, no trailing text.
            - Strings must be valid JSON (escape quotes and newlines).
            - Use null (not "null") for missing string fields. Use [] for empty arrays.
            - Keep summary <= 3 sentences. Keep each list item <= 1 sentence.
            - Match the caller's language for callerName, mainConcerns, unansweredQuestions
              (other fields stay English).
            - interestRating MUST be a JSON number, not a string. One decimal max.
            - leadIntent rules:
              * isAppointment=true ONLY if caller is clearly trying to book a future visit.
                General product/pricing questions are NOT appointment intent.
              * isHumanRequest=true if caller explicitly asked for a human OR if
                callbackNeeded=true (mirror that signal).
              * DO NOT invent dates. If caller only said "next week" without a day,
                leave structuredSlots empty and suggestedDatetime null.
              * Resolving relative dates: "tomorrow" / "next Monday" / "23rd" should
                be converted to a concrete YYYY-MM-DD based on today's date as
                shown in the transcript header. When in doubt, null is safer than wrong.
              * When isAppointment=false, set service / preferredWindowRaw /
                structuredSlots / suggestedDatetime to null / [].
            """;

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
                .systemPrompt(SYSTEM_PROMPT)
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
