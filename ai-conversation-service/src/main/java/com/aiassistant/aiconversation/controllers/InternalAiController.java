package com.aiassistant.aiconversation.controllers;

import com.aiassistant.aiconversation.configuration.ServiceConfiguration;
import com.aiassistant.aiconversation.exceptions.NotFoundException;
import com.aiassistant.aiconversation.llm.LlmProvider;
import com.aiassistant.aiconversation.llm.LlmProviderRegistry;
import com.aiassistant.aiconversation.llm.LlmReply;
import com.aiassistant.aiconversation.llm.LlmRequest;
import com.aiassistant.aiconversation.models.request.RespondRequest;
import com.aiassistant.aiconversation.models.request.SummariseRequest;
import com.aiassistant.aiconversation.models.response.RespondResponse;
import com.aiassistant.aiconversation.models.response.SessionInfoResponse;
import com.aiassistant.aiconversation.models.response.SummariseResponse;
import com.aiassistant.aiconversation.services.SummaryService;
import com.aiassistant.aiconversation.session.ConversationSession;
import com.aiassistant.aiconversation.session.SessionRegistry;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal/ai")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('SCOPE_ai.internal.invoke')")
public class InternalAiController {

    private final LlmProviderRegistry providerRegistry;
    private final ServiceConfiguration serviceConfiguration;
    private final SummaryService summaryService;
    private final SessionRegistry sessionRegistry;

    @PostMapping("/respond")
    public ResponseEntity<RespondResponse> respond(@Valid @RequestBody RespondRequest req) {
        LlmProvider provider = providerRegistry.get(req.getProvider());
        ServiceConfiguration.Llm cfg = serviceConfiguration.getLlm();
        LlmRequest llmReq = LlmRequest.builder()
                .systemPrompt(req.getSystemPrompt())
                .messages(req.getMessages())
                .maxOutputTokens(req.getMaxOutputTokens() != null ? req.getMaxOutputTokens() : cfg.getMaxOutputTokens())
                .temperature(req.getTemperature() != null ? req.getTemperature() : cfg.getTemperature())
                .cacheSystemPrompt(cfg.isPromptCacheEnabled())
                .build();
        LlmReply reply = provider.complete(llmReq);
        return ResponseEntity.ok(RespondResponse.builder()
                .text(reply.getText())
                .finishReason(reply.getFinishReason())
                .usage(reply.getUsage())
                .provider(provider.id())
                .build());
    }

    @PostMapping("/summarise")
    public ResponseEntity<SummariseResponse> summarise(@Valid @RequestBody SummariseRequest req) {
        return ResponseEntity.ok(summaryService.summarise(req));
    }

    @GetMapping("/conversations/{conversationId}")
    public ResponseEntity<SessionInfoResponse> conversation(@PathVariable String conversationId) {
        ConversationSession s = sessionRegistry.get(conversationId);
        if (s == null) throw new NotFoundException("Conversation not found: " + conversationId);
        return ResponseEntity.ok(SessionInfoResponse.builder()
                .conversationId(s.getConversationId())
                .businessId(s.getBusinessId())
                .provider(s.getProvider().id())
                .hasKnowledge(s.hasKnowledge())
                .messageCount(s.snapshotMessages(0).size())
                .pendingMessageCount(s.getPendingMessages().size())
                .usage(s.getUsage().get())
                .createdAt(s.getCreatedAt())
                .lastActivityAt(s.getLastActivityAt())
                .build());
    }
}
