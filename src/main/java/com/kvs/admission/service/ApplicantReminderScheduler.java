package com.kvs.admission.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Automatically dispatches WhatsApp reminders for upcoming entrance evaluations (US-02009).
 * Disabled unless {@code admission.reminders.auto-dispatch.enabled=true}.
 */
@Component
@EnableScheduling
@ConditionalOnProperty(prefix = "admission.reminders.auto-dispatch", name = "enabled", havingValue = "true")
@Slf4j
public class ApplicantReminderScheduler {

    @Autowired
    private ApplicantReminderService reminderService;

    @Value("${admission.reminders.auto-dispatch.lookahead-hours:24}")
    private int lookaheadHours;

    @Scheduled(cron = "${admission.reminders.auto-dispatch.cron:0 0 * * * *}")
    public void dispatchDueReminders() {
        try {
            reminderService.dispatchAllDueReminders(lookaheadHours);
        } catch (Exception e) {
            log.error("Automatic entrance evaluation reminder dispatch failed", e);
        }
    }
}
