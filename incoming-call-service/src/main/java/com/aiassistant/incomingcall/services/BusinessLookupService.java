package com.aiassistant.incomingcall.services;

import com.aiassistant.incomingcall.clients.UserBusinessClient;
import com.aiassistant.incomingcall.models.response.BusinessLookupResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BusinessLookupService {

    private final UserBusinessClient userBusinessClient;

    public BusinessLookupResponse lookupByTwilioNumber(String twilioNumber) {
        return userBusinessClient.lookupByTwilioNumber(twilioNumber);
    }
}
