package com.aiassistant.aiconversation.session;

import com.aiassistant.aiconversation.configuration.ServiceConfiguration;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Builds the system prompt the LLM sees on every turn. Combines the
 * voice-call persona/rules with the rendered business {@code knowledge}
 * blob handed in via the {@code INIT} WS frame.
 *
 * <p>The template is loaded once at startup. By default it comes from the
 * classpath resource {@code prompts/system-prompt.txt}. If
 * {@code configs.prompts.systemPromptPath} is set in service config, that
 * filesystem path is read instead — letting operators edit the prompt in
 * production without rebuilding the service (restart still required).
 *
 * <p>Template placeholders:
 * <ul>
 *   <li>{@code {CALLBACK}} — replaced with the literal {@link #CALLBACK_NEEDED} sentinel</li>
 *   <li>{@code {KNOWLEDGE}} — replaced with the rendered business knowledge blob</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class SystemPromptBuilder {

    private static final Logger log = LoggerFactory.getLogger(SystemPromptBuilder.class);

    public static final String CALLBACK_NEEDED = "CALLBACK_NEEDED";
    public static final String HANGUP = "HANGUP";

    private static final String CLASSPATH_DEFAULT = "prompts/system-prompt.txt";
    private static final String PH_CALLBACK = "{CALLBACK}";
    private static final String PH_HANGUP = "{HANGUP}";
    private static final String PH_KNOWLEDGE = "{KNOWLEDGE}";

    private final ServiceConfiguration serviceConfiguration;

    private volatile String template;

    @PostConstruct
    void load() {
        ServiceConfiguration.Prompts cfg = serviceConfiguration.getPrompts();
        String externalPath = cfg == null ? null : cfg.getSystemPromptPath();
        if (externalPath != null && !externalPath.isBlank()) {
            try {
                this.template = Files.readString(Path.of(externalPath), StandardCharsets.UTF_8);
                log.info("Loaded system prompt from external file: {}", externalPath);
                return;
            } catch (IOException e) {
                log.warn("Failed to read system prompt from {} ({}). Falling back to classpath default.",
                        externalPath, e.getMessage());
            }
        }
        try {
            ClassPathResource resource = new ClassPathResource(CLASSPATH_DEFAULT);
            this.template = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            log.info("Loaded system prompt from classpath: {}", CLASSPATH_DEFAULT);
        } catch (IOException e) {
            throw new IllegalStateException("System prompt template not found at classpath:" + CLASSPATH_DEFAULT, e);
        }
    }

    public String build(String knowledge) {
        String safe = knowledge == null ? "" : knowledge.trim();
        return template
                .replace(PH_CALLBACK, CALLBACK_NEEDED)
                .replace(PH_HANGUP, HANGUP)
                .replace(PH_KNOWLEDGE, safe);
    }
}
