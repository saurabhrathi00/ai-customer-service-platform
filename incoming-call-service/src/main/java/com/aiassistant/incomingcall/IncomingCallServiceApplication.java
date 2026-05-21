package com.aiassistant.incomingcall;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.PropertySource;

@SpringBootApplication
@PropertySource("classpath:application.properties")
public class IncomingCallServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(IncomingCallServiceApplication.class, args);
    }
}
