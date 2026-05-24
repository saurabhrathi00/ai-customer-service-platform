package com.aiassistant.userbusiness.repository;

import com.aiassistant.userbusiness.enums.LeadStatus;
import com.aiassistant.userbusiness.models.dao.LeadEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface LeadRepository extends JpaRepository<LeadEntity, String> {

    /** Idempotency probe for the create endpoint. */
    Optional<LeadEntity> findByCallLogId(String callLogId);

    /** Dashboard listing — newest first. */
    List<LeadEntity> findByBusinessIdOrderByCreatedAtDesc(String businessId);

    /** Reminder scheduler claim. Returns NEW leads whose next_reminder_at
     *  is in the past, across all businesses. Oldest first so the worker
     *  drains FIFO. */
    List<LeadEntity> findByStatusAndNextReminderAtLessThanEqualOrderByNextReminderAtAsc(
            LeadStatus status, Instant cutoff);

    /** notification-service fan-out: leads we haven't told the owner about. */
    List<LeadEntity> findByOwnerNotifiedAtIsNullOrderByCreatedAtAsc();

    /** notification-service fan-out: terminal leads (APPROVED / DECLINED) whose
     *  customer hasn't been told yet. IGNORED is excluded because we never
     *  notify the customer for it. */
    @org.springframework.data.jpa.repository.Query(
            "SELECT l FROM LeadEntity l WHERE l.customerNotifiedAt IS NULL " +
            "AND l.status IN (com.aiassistant.userbusiness.enums.LeadStatus.APPROVED, " +
            "                  com.aiassistant.userbusiness.enums.LeadStatus.DECLINED) " +
            "ORDER BY l.decidedAt ASC")
    List<LeadEntity> findPendingCustomerNotifications();
}
