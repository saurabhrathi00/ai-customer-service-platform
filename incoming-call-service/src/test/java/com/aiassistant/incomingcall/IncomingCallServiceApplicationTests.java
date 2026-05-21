package com.aiassistant.incomingcall;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
        "secrets.twilio.account-sid=ACtest",
        "secrets.twilio.auth-token=testtoken",
        "secrets.authService.clientId=incoming-call-service",
        "secrets.authService.clientSecret=secret",
        "configs.userBusinessService.baseUrl=http://localhost:8082/user-business-service",
        "configs.authService.baseUrl=http://localhost:8081/auth-service",
        "configs.authService.audience=user-business-service",
        "configs.authService.scopes=business.internal.read",
        "configs.callOrchestration.wsBaseUrl=wss://localhost:8084"
})
class IncomingCallServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}
