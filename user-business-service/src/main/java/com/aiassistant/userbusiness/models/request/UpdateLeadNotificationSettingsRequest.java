package com.aiassistant.userbusiness.models.request;

import com.aiassistant.userbusiness.enums.ReminderMode;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class UpdateLeadNotificationSettingsRequest {

    @DecimalMin("0.0")
    @DecimalMax("10.0")
    private BigDecimal highInterestThreshold;

    private ReminderMode reminderMode;

    @Min(1)
    @Max(240)
    private Integer reminderIntervalMinutes;

    @Min(1)
    @Max(10)
    private Integer maxReminders;
}
