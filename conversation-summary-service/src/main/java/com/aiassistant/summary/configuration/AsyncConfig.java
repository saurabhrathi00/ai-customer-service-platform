package com.aiassistant.summary.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class AsyncConfig {

    /** Pool for the trigger → LLM → persist pipeline. Small core; tasks
     *  are I/O bound (LLM ~1-3s) so a modest pool covers typical concurrent
     *  call-end traffic. */
    @Bean(name = "summaryExecutor")
    public Executor summaryExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("summary-");
        executor.initialize();
        return executor;
    }
}
