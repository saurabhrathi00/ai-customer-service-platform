package com.aiassistant.knowledge.models.request;

import com.aiassistant.knowledge.models.domain.BusinessHours;
import com.aiassistant.knowledge.models.domain.Service;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.hibernate.validator.constraints.URL;

import java.util.List;

@Data
public class UpsertProfileRequest {

    private BusinessHours businessHours;

    @Size(max = 1000)
    private String address;

    @Size(max = 500)
    private String locationNotes;

    @Pattern(regexp = "^\\+?[1-9]\\d{6,14}$", message = "altPhone must be E.164")
    private String altPhone;

    @Email
    @Size(max = 200)
    private String contactEmail;

    @URL(protocol = "https", message = "websiteUrl must be https")
    @Size(max = 500)
    private String websiteUrl;

    private List<@Size(min = 2, max = 8) String> languagesSpoken;

    private List<Service> servicesOffered;

    private List<@Size(min = 1, max = 32) String> paymentMethods;

    @Size(max = 2000)
    private String appointmentPolicy;

    @Size(max = 2000)
    private String cancellationPolicy;

    @Size(max = 2000)
    private String refundPolicy;
}
