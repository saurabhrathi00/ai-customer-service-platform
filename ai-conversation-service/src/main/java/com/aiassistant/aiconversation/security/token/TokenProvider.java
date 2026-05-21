package com.aiassistant.aiconversation.security.token;

public interface TokenProvider {
    String tokenType();
    boolean validate(String token);
    TokenPrincipal parse(String token);
}