package com.aiassistant.auth.models.request;


import lombok.Data;

import java.util.List;

@Data
public class ServiceTokenRequest {
    private String clientId;
    private String clientSecret;
    private String audience;
    private List<String> scopes = null;
}
