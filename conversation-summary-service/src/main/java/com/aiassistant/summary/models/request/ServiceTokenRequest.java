package com.aiassistant.summary.models.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ServiceTokenRequest {
    private String clientId;
    private String clientSecret;
    private String audience;
    private List<String> scopes;
}
