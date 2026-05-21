package com.aiassistant.callorchestration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.PropertySource;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync
@EnableScheduling
@PropertySource("classpath:application.properties")
public class CallOrchestrationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CallOrchestrationServiceApplication.class, args);
    }
}