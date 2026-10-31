package com.kvs.admission.notification;

import com.kvs.admission.entity.AdmissionEnquiry;
import com.kvs.admission.entity.ApplicantNotification;
import com.kvs.admission.enums.NotificationChannel;
import com.kvs.admission.enums.NotificationStatus;
import com.kvs.admission.repo.ApplicantNotificationRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * The email copy of a WhatsApp notification (offer, entrance reminder) to the guardian's email address, sent from the
 * Admissions mailbox in Settings → Integrations (else the school's main email account). Recorded as its own notification row
 * (channel EMAIL); not sent when the guardian has no email address. A copy that could not be sent is FAILED with the reason.
 */
@Component
public class AdmissionEmailCopy {

    private final SchoolMailClient mail;
    private final ApplicantNotificationRepository notifications;

    public AdmissionEmailCopy(SchoolMailClient mail, ApplicantNotificationRepository notifications) {
        this.mail = mail;
        this.notifications = notifications;
    }

    /** whatsApp: the WhatsApp row just saved (its ids and message are copied); returns null when there is no guardian email */
    public ApplicantNotification send(AdmissionEnquiry enquiry, ApplicantNotification whatsApp, String subject) {
        String to = enquiry.getGuardianEmail();
        if (to == null || to.isBlank()) return null;
        ApplicantNotification n = new ApplicantNotification();
        n.setTenantId(whatsApp.getTenantId());
        n.setEnquiryId(whatsApp.getEnquiryId());
        n.setExamId(whatsApp.getExamId());
        n.setAdmitCardId(whatsApp.getAdmitCardId());
        n.setOfferId(whatsApp.getOfferId());
        n.setChannel(NotificationChannel.EMAIL);
        n.setNotificationType(whatsApp.getNotificationType());
        n.setRecipientPhone(whatsApp.getRecipientPhone());
        n.setRecipientEmail(to.trim());
        n.setMessageBody(whatsApp.getMessageBody());
        n.setDispatchedAt(Instant.now());
        SchoolMailClient.Outcome o = mail.send(enquiry.getTenantId(), to.trim(), subject, whatsApp.getMessageBody(), null,
                "application " + enquiry.getApplicationNumber());
        n.setStatus(o.status().equals("SENT") ? NotificationStatus.SENT : NotificationStatus.FAILED);
        n.setFailureReason(o.status().equals("SENT") ? null : clip(o.detail()));
        n.setProviderMessageId(o.status().equals("SENT") ? clip(o.detail(), 100) : null);
        return notifications.save(n);
    }

    private static String clip(String s) { return clip(s, 500); }
    private static String clip(String s, int n) { return s == null ? null : s.length() > n ? s.substring(0, n) : s; }
}
