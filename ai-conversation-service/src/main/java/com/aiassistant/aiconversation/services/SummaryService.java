package com.aiassistant.aiconversation.services;

import com.aiassistant.aiconversation.configuration.ServiceConfiguration;
import com.aiassistant.aiconversation.llm.LlmMessage;
import com.aiassistant.aiconversation.llm.LlmProvider;
import com.aiassistant.aiconversation.llm.LlmProviderRegistry;
import com.aiassistant.aiconversation.llm.LlmReply;
import com.aiassistant.aiconversation.llm.LlmRequest;
import com.aiassistant.aiconversation.models.request.SummariseRequest;
import com.aiassistant.aiconversation.models.response.SummariseResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SummaryService {

    private static final String SYSTEM_PROMPT = """
            You summarise customer service phone calls.
            Output a short, neutral 2-3 sentence summary capturing intent,
            outcome, and any follow-up action. No preamble, no bullet points.
            """;

    private final LlmProviderRegistry providerRegistry;
    private final ServiceConfiguration serviceConfiguration;

    public SummariseResponse summarise(SummariseRequest req) {
        LlmProvider provider = providerRegistry.get(req.getProvider());
        StringBuilder transcript = new StringBuilder();
        for (SummariseRequest.TranscriptLine line : req.getTranscript()) {
            transcript.append(line.getSpeaker()).append(": ").append(line.getText()).append('\n');
        }
        ServiceConfiguration.Summary cfg = serviceConfiguration.getSummary();
        int maxTokens = req.getMaxOutputTokens() != null ? req.getMaxOutputTokens() : cfg.getMaxOutputTokens();

        LlmRequest llmReq = LlmRequest.builder()
                .systemPrompt(SYSTEM_PROMPT)
                .messages(List.of(LlmMessage.builder()
                        .role(LlmMessage.Role.USER)
                        .content("Transcript:\n" + transcript)
                        .build()))
                .maxOutputTokens(maxTokens)
                .temperature(cfg.getTemperature())
                .cacheSystemPrompt(false)
                .build();

        LlmReply reply = provider.complete(llmReq);
        return SummariseResponse.builder()
                .summary(reply.getText())
                .usage(reply.getUsage())
                .provider(provider.id())
                .build();
    }
}
