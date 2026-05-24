package com.aiassistant.notification.services;

import com.aiassistant.notification.clients.UserBusinessLeadClient;
import com.aiassistant.notification.models.dto.LeadDto;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Top-level scheduler. Runs three independent fan-outs every minute:
 * <ol>
 *   <li>New leads → owner WhatsApp + mark owner_notified_at</li>
 *   <li>Due reminders → owner WhatsApp + bump reminders_sent + recompute next_reminder_at</li>
 *   <li>Terminal decisions → customer WhatsApp + mark customer_notified_at</li>
 * </ol>
 *
 * <p>Each pull is independent of the others — a failure in one queue
 * doesn't block the rest. Individual lead failures are logged inside the
 * dispatcher; the lead stays unmarked and is retried next tick.</p>
 */
@Component
@RequiredArgsConstructor
public class NotificationScheduler {

    private static final Logger log = LoggerFactory.getLogger(NotificationScheduler.class);

    private final UserBusinessLeadClient client;
    private final NotificationDispatcher dispatcher;

    @Scheduled(cron = "${configs.scheduler.cron:0 * * * * *}")
    public void tick() {
        drainOwnerNotifications();
        drainReminders();
        drainCustomerNotifications();
    }

    private void drainOwnerNotifications() {
        try {
            List<LeadDto> pending = client.pendingOwnerNotifications();
            if (pending.isEmpty()) return;
            log.info("[sched] owner-notifications n={}", pending.size());
            for (LeadDto lead : pending) {
                dispatcher.notifyOwnerOfNewLead(lead);
            }
        } catch (Exception ex) {
            log.error("[sched] owner-notifications drain failed: {}", ex.getMessage());
        }
    }

    private void drainReminders() {
        try {
            List<LeadDto> due = client.dueReminders();
            if (due.isEmpty()) return;
            log.info("[sched] reminders n={}", due.size());
            for (LeadDto lead : due) {
                // Skip the first ping — the dedicated owner-notification path
                // handles that and we don't want a double-fire when the two
                // pipelines briefly overlap.
                if (lead.getOwnerNotifiedAt() == null) continue;
                dispatcher.sendReminderToOwner(lead);
            }
        } catch (Exception ex) {
            log.error("[sched] reminders drain failed: {}", ex.getMessage());
        }
    }

    private void drainCustomerNotifications() {
        try {
            List<LeadDto> pending = client.pendingCustomerNotifications();
            if (pending.isEmpty()) return;
            log.info("[sched] customer-notifications n={}", pending.size());
            for (LeadDto lead : pending) {
                dispatcher.notifyCustomerOfDecision(lead);
            }
        } catch (Exception ex) {
            log.error("[sched] customer-notifications drain failed: {}", ex.getMessage());
        }
    }
}
