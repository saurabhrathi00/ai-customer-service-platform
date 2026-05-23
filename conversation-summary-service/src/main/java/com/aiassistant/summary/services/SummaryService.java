package com.aiassistant.summary.services;

import com.aiassistant.summary.clients.CallOrchestrationClient;
import com.aiassistant.summary.configuration.ServiceConfiguration;
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
              "interestRating": <integer 1-5; how interested the caller seemed in doing business with the company. 1=cold/wrong-number, 3=mild interest, 5=ready to buy. When signal is mixed, use 3>,
              "interestReason": "<one short sentence justifying the rating>",
              "mainConcerns": ["<concern or question in caller's own words>", ...],
              "callbackNeeded": <true if the caller explicitly asked for a human OR the assistant could not answer a substantive question; else false>,
              "callbackReason": "<one short sentence if callbackNeeded=true, else null>",
              "unansweredQuestions": ["<question the assistant could not answer>", ...]
            }

            Hard rules:
            - Output ONLY the JSON object. No code fences, no preamble, no trailing text.
            - Strings must be valid JSON (escape quotes and newlines).
            - Use null (not "null") for missing string fields. Use [] for empty arrays.
            - Keep summary <= 3 sentences. Keep each list item <= 1 sentence.
            - Match the caller's language for callerName, mainConcerns, unansweredQuestions
              (other fields stay English).
            """;

    private final LlmProviderRegistry providerRegistry;
    private final ServiceConfiguration serviceConfiguration;
    private final CallOrchestrationClient callOrchClient;
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

            CallSummaryEntity entity = runLlmAndBuildEntity(callLogId, payload);
            persist(entity);
            log.info("[summary] persisted callLogId={} interest={} queryType={} callback={}",
                    callLogId, entity.getInterestRating(), entity.getQueryType(),
                    entity.getCallbackNeeded());
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

    private CallSummaryEntity runLlmAndBuildEntity(String callLogId, TranscriptPayload payload) {
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
        return CallSummaryEntity.builder()
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
    }

    private String buildUserPrompt(TranscriptPayload p) {
        StringBuilder sb = new StringBuilder();
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
            p.interestRating = intVal(node, "interestRating");
            p.interestReason = text(node, "interestReason");
            p.mainConcerns = stringList(node, "mainConcerns");
            p.callbackNeeded = boolVal(node, "callbackNeeded");
            p.callbackReason = text(node, "callbackReason");
            p.unansweredQuestions = stringList(node, "unansweredQuestions");
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

    /** Tiny POJO so the parser can hand fields back without 9 method args. */
    private static final class ParsedSummary {
        String summary;
        String callerName;
        String queryType;
        Integer interestRating;
        String interestReason;
        List<String> mainConcerns;
        Boolean callbackNeeded;
        String callbackReason;
        List<String> unansweredQuestions;
    }
}
