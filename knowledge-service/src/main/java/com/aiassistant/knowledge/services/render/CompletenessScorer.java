package com.aiassistant.knowledge.services.render;

import com.aiassistant.knowledge.models.dao.BusinessFreeformEntity;
import com.aiassistant.knowledge.models.dao.BusinessProfileEntity;
import com.aiassistant.knowledge.models.domain.BusinessHours;
import com.aiassistant.knowledge.models.domain.Service;
import com.aiassistant.knowledge.services.mapper.KnowledgeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure function: knowledge state → {score, missingFields}. Used both to stamp
 * `business_profile.completeness_score` and to power dashboard nudges.
 */
@Component
@RequiredArgsConstructor
public class CompletenessScorer {

    public static final int MAX = 100;

    private final KnowledgeMapper mapper;

    public Result score(BusinessProfileEntity profile,
                        BusinessFreeformEntity freeform,
                        long activeFaqCount,
                        long activeEscalationCount) {
        int score = 0;
        List<String> missing = new ArrayList<>();

        // Hours (15)
        BusinessHours hours = profile == null ? null
                : mapper.readJson(profile.getBusinessHoursJson(), BusinessHours.class);
        if (hasHours(hours)) score += 15; else missing.add("business_hours");

        // Address + location (10)
        if (profile != null && hasText(profile.getAddress())) score += 10;
        else missing.add("address");

        // Contact (5) — alt phone or email
        if (profile != null && (hasText(profile.getAltPhone()) || hasText(profile.getContactEmail()))) score += 5;
        else missing.add("contact");

        // Services (20) — ≥1 entry
        List<Service> services = profile == null ? null
                : mapper.readJsonList(profile.getServicesOfferedJson());
        if (services != null && !services.isEmpty()) score += 20;
        else missing.add("services_offered");

        // Payment methods (5)
        if (profile != null && profile.getPaymentMethods() != null && !profile.getPaymentMethods().isEmpty())
            score += 5;
        else missing.add("payment_methods");

        // Appointment policy (10)
        if (profile != null && hasText(profile.getAppointmentPolicy())) score += 10;
        else missing.add("appointment_policy");

        // Cancellation policy (10)
        if (profile != null && hasText(profile.getCancellationPolicy())) score += 10;
        else missing.add("cancellation_policy");

        // Languages (5)
        if (profile != null && profile.getLanguagesSpoken() != null && !profile.getLanguagesSpoken().isEmpty())
            score += 5;
        else missing.add("languages_spoken");

        // ≥3 FAQs (10)
        if (activeFaqCount >= 3) score += 10;
        else missing.add("faqs_min_3");

        // Free-form text non-empty (5)
        if (freeform != null && hasText(freeform.getContent())) score += 5;
        else missing.add("freeform_content");

        // ≥1 escalation rule (5)
        if (activeEscalationCount >= 1) score += 5;
        else missing.add("escalation_rules_min_1");

        return new Result(Math.min(MAX, score), List.copyOf(missing));
    }

    private static boolean hasText(String s) { return s != null && !s.trim().isEmpty(); }

    private static boolean hasHours(BusinessHours h) {
        if (h == null || h.getDays() == null || h.getDays().isEmpty()) return false;
        return h.getDays().values().stream().anyMatch(d ->
                d != null && (Boolean.TRUE.equals(d.getClosed())
                        || (hasText(d.getOpen()) && hasText(d.getClose()))));
    }

    public record Result(int score, List<String> missingFields) {}
}
