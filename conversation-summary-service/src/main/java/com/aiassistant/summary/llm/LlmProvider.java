package com.aiassistant.summary.llm;

import reactor.core.publisher.Flux;

public interface LlmProvider {

    String id();

    Flux<LlmDelta> streamReply(LlmRequest request);

    LlmReply complete(LlmRequest request);
}
