package com.aiassistant.knowledge.models.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class FreeformRequest {

    @Size(max = 10_000, message = "content must be 10000 chars or fewer")
    private String content;
}
