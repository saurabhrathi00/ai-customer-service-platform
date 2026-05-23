package com.aiassistant.callorchestration.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@Configuration
public class AsyncConfig {

    /** Shared 1-thread scheduler for per-call silence watchdog ticks.
     *  Tasks are lightweight (a few atomic reads + occasional TTS call),
     *  so one thread comfortably handles many concurrent calls. */
    @Bean(name = "silenceWatchdogScheduler", destroyMethod = "shutdown")
    public ScheduledExecutorService silenceWatchdogScheduler() {
        return Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "silence-watchdog");
            t.setDaemon(true);
            return t;
        });
    }

    @Bean(name = "postCallExecutor")
    public Executor postCallExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("postcall-");
        executor.initialize();
        return executor;
    }

    @Bean(name = "ttsExecutor")
    public Executor ttsExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(32);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("tts-");
        executor.initialize();
        return executor;
    }
}
