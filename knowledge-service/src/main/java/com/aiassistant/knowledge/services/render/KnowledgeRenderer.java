package com.aiassistant.knowledge.services.render;

import com.aiassistant.knowledge.models.dao.BusinessEscalationRuleEntity;
import com.aiassistant.knowledge.models.dao.BusinessFaqEntity;
import com.aiassistant.knowledge.models.dao.BusinessFreeformEntity;
import com.aiassistant.knowledge.models.dao.BusinessProfileEntity;
import com.aiassistant.knowledge.models.domain.BusinessHours;
import com.aiassistant.knowledge.models.domain.Service;
import com.aiassistant.knowledge.services.mapper.KnowledgeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Pure function: knowledge state → LLM-ready text block. No side effects, no I/O.
 * Output format is tested via snapshot tests; do not change layout casually.
 */
@Component
@RequiredArgsConstructor
public class KnowledgeRenderer {

    private static final List<String> DAY_ORDER = List.of("mon", "tue", "wed", "thu", "fri", "sat", "sun");
    private static final Map<String, String> DAY_LABEL = Map.of(
            "mon", "Monday", "tue", "Tuesday", "wed", "Wednesday",
            "thu", "Thursday", "fri", "Friday", "sat", "Saturday", "sun", "Sunday"
    );

    private final KnowledgeMapper mapper;

    public String render(String businessName,
                         BusinessProfileEntity profile,
                         BusinessFreeformEntity freeform,
                         List<BusinessFaqEntity> activeFaqs,
                         List<BusinessEscalationRuleEntity> activeRules) {

        StringBuilder out = new StringBuilder();
        out.append("=== BUSINESS PROFILE: ").append(businessName == null ? "(unknown)" : businessName).append(" ===\n");

        if (profile != null) {
            renderHours(out, mapper.readJson(profile.getBusinessHoursJson(), BusinessHours.class));
            renderLocation(out, profile.getAddress(), profile.getLocationNotes());
            renderContact(out, profile.getAltPhone(), profile.getContactEmail(), profile.getWebsiteUrl());
            renderLanguages(out, profile.getLanguagesSpoken());
            renderServices(out, mapper.readJsonList(profile.getServicesOfferedJson()));
            renderList(out, "PAYMENT METHODS", profile.getPaymentMethods());
            renderBlock(out, "APPOINTMENT POLICY", profile.getAppointmentPolicy());
            renderBlock(out, "CANCELLATION", profile.getCancellationPolicy());
            renderBlock(out, "REFUND", profile.getRefundPolicy());
        }

        if (freeform != null && hasText(freeform.getContent())) {
            out.append("\n=== ADDITIONAL CONTEXT ===\n").append(freeform.getContent().trim()).append('\n');
        }

        if (activeFaqs != null && !activeFaqs.isEmpty()) {
            out.append("\n=== FREQUENTLY ASKED QUESTIONS ===\n");
            for (BusinessFaqEntity faq : activeFaqs) {
                out.append("Q: ").append(faq.getQuestion().trim()).append('\n');
                out.append("A: ").append(faq.getAnswer().trim()).append("\n\n");
            }
        }

        if (activeRules != null && !activeRules.isEmpty()) {
            out.append("=== ESCALATION RULES (internal, do not read aloud to caller) ===\n");
            for (BusinessEscalationRuleEntity r : activeRules) {
                out.append("- If customer mentions \"").append(r.getTriggerPhrase().trim())
                        .append("\" → ").append(r.getAction());
                if (hasText(r.getActionMessage())) {
                    out.append(". Say: \"").append(r.getActionMessage().trim()).append("\"");
                }
                out.append('\n');
            }
        }

        return out.toString();
    }

    // -- sections ----------------------------------------------------------

    private void renderHours(StringBuilder out, BusinessHours hours) {
        if (hours == null || (hours.getDays() == null && hours.getHolidays() == null)) return;
        out.append("\nHOURS:\n");
        if (hours.getDays() != null) {
            for (String key : DAY_ORDER) {
                BusinessHours.DayHours dh = hours.getDays().get(key);
                if (dh == null) continue;
                String label = DAY_LABEL.get(key);
                if (Boolean.TRUE.equals(dh.getClosed())) {
                    out.append("- ").append(label).append(": Closed\n");
                } else if (hasText(dh.getOpen()) && hasText(dh.getClose())) {
                    out.append("- ").append(label).append(": ").append(dh.getOpen())
                            .append(" - ").append(dh.getClose()).append('\n');
                }
            }
        }
        if (hours.getHolidays() != null && !hours.getHolidays().isEmpty()) {
            out.append("- Holidays: ");
            for (int i = 0; i < hours.getHolidays().size(); i++) {
                LocalDate d = hours.getHolidays().get(i);
                if (i > 0) out.append(", ");
                out.append(d.toString());
            }
            out.append('\n');
        }
    }

    private void renderLocation(StringBuilder out, String address, String notes) {
        if (!hasText(address) && !hasText(notes)) return;
        out.append("\nLOCATION:\n");
        if (hasText(address)) out.append(address.trim());
        if (hasText(notes)) out.append(hasText(address) ? ". " : "").append(notes.trim());
        out.append('\n');
    }

    private void renderContact(StringBuilder out, String phone, String email, String website) {
        if (!hasText(phone) && !hasText(email) && !hasText(website)) return;
        out.append("\nCONTACT:\n");
        if (hasText(phone)) out.append("- Alt phone: ").append(phone.trim()).append('\n');
        if (hasText(email)) out.append("- Email: ").append(email.trim()).append('\n');
        if (hasText(website)) out.append("- Website: ").append(website.trim()).append('\n');
    }

    private void renderLanguages(StringBuilder out, List<String> codes) {
        if (codes == null || codes.isEmpty()) return;
        out.append("\nLANGUAGES: ");
        for (int i = 0; i < codes.size(); i++) {
            if (i > 0) out.append(", ");
            out.append(prettyLanguage(codes.get(i)));
        }
        out.append('\n');
    }

    private void renderServices(StringBuilder out, List<Service> services) {
        if (services == null || services.isEmpty()) return;
        out.append("\nSERVICES:\n");
        for (Service s : services) {
            out.append("- ").append(hasText(s.getName()) ? s.getName().trim() : "(unnamed)");
            String price = priceRange(s);
            if (hasText(price)) out.append(": ").append(price);
            if (s.getDurationMinutes() != null && s.getDurationMinutes() > 0) {
                out.append(hasText(price) ? ", " : ": ").append(s.getDurationMinutes()).append(" min");
            }
            if (hasText(s.getDescription())) out.append(" — ").append(s.getDescription().trim());
            out.append('\n');
        }
    }

    private void renderList(StringBuilder out, String label, List<String> values) {
        if (values == null || values.isEmpty()) return;
        out.append('\n').append(label).append(": ");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) out.append(", ");
            out.append(prettyValue(values.get(i)));
        }
        out.append('\n');
    }

    private void renderBlock(StringBuilder out, String label, String value) {
        if (!hasText(value)) return;
        out.append('\n').append(label).append(":\n").append(value.trim()).append('\n');
    }

    // -- helpers -----------------------------------------------------------

    private static boolean hasText(String s) { return s != null && !s.trim().isEmpty(); }

    private static String prettyValue(String s) {
        if (s == null) return "";
        // "cash" -> "Cash"; "credit_card" -> "Credit Card"
        String spaced = s.replace('_', ' ').trim().toLowerCase(Locale.ROOT);
        StringBuilder b = new StringBuilder(spaced.length());
        boolean upper = true;
        for (char c : spaced.toCharArray()) {
            b.append(upper ? Character.toUpperCase(c) : c);
            upper = Character.isWhitespace(c);
        }
        return b.toString();
    }

    private static String prettyLanguage(String code) {
        if (code == null) return "";
        String c = code.trim().toLowerCase(Locale.ROOT);
        return switch (c) {
            case "en" -> "English";
            case "hi" -> "Hindi";
            case "mr" -> "Marathi";
            case "ta" -> "Tamil";
            case "te" -> "Telugu";
            case "bn" -> "Bengali";
            case "gu" -> "Gujarati";
            case "pa" -> "Punjabi";
            case "kn" -> "Kannada";
            case "ml" -> "Malayalam";
            case "ur" -> "Urdu";
            default -> prettyValue(c);
        };
    }

    private static String priceRange(Service s) {
        if (s.getPriceMin() == null && s.getPriceMax() == null) return null;
        String cur = hasText(s.getPriceCurrency()) ? s.getPriceCurrency().trim() : "";
        if (s.getPriceMin() != null && s.getPriceMax() != null) {
            if (s.getPriceMin().equals(s.getPriceMax())) return cur + " " + s.getPriceMin();
            return cur + " " + s.getPriceMin() + "-" + s.getPriceMax();
        }
        return cur + " " + (s.getPriceMin() != null ? s.getPriceMin() : s.getPriceMax());
    }
}
