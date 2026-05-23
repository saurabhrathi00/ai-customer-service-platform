package com.aiassistant.summary;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.PropertySource;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync
@EnableScheduling
@PropertySource("classpath:application.properties")
public class ConversationSummaryServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(ConversationSummaryServiceApplication.class, args);
    }
}