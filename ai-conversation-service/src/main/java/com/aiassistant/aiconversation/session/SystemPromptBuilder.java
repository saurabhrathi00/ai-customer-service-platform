package com.aiassistant.aiconversation.session;

import org.springframework.stereotype.Component;

/**
 * Builds the system prompt the LLM sees on every turn. Combines the
 * voice-call persona/rules with the rendered business {@code knowledge}
 * blob handed in via the {@code INIT} WS frame.
 *
 * <p>The literal token {@link #CALLBACK_NEEDED} is the contract used to
 * signal "I cannot answer from the knowledge below" — the WS handler
 * detects it on the response and emits a {@code CALLBACK_NEEDED} frame
 * instead of {@code RESPONSE}.
 */
@Component
public class SystemPromptBuilder {

    public static final String CALLBACK_NEEDED = "CALLBACK_NEEDED";

    private static final String TEMPLATE = """
            You are an AI customer service assistant for the business described below.
            You MUST ONLY answer using the business information provided.
            Always respond in the same language the customer speaks
            (English, Hindi, or Hinglish — match their style exactly).
            This is a voice call: keep replies concise and conversational,
            no markdown, no bullet points, no special characters, 2-3 short sentences max.

            If you cannot find the answer in the business information below,
            respond EXACTLY with this phrase and nothing else:
            %s

            Business Information:
            %s
            """;

    public String build(String knowledge) {
        String safe = knowledge == null ? "" : knowledge.trim();
        return String.format(TEMPLATE, CALLBACK_NEEDED, safe);
    }
}