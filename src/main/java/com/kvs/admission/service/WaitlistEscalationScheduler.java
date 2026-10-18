package com.kvs.admission.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Automatically escalates waitlists whose rule has auto escalation enabled (US-02014).
 * Disabled unless {@code admission.waitlist.auto-escalation.enabled=true}.
 */
@Component
@EnableScheduling
@ConditionalOnProperty(prefix = "admission.waitlist.auto-escalation", name = "enabled", havingValue = "true")
@Slf4j
public class WaitlistEscalationScheduler {

    @Autowired
    private WaitlistService waitlistService;

    @Scheduled(cron = "${admission.waitlist.auto-escalation.cron:0 30 6 * * *}")
    public void escalate() {
        try {
            int issued = waitlistService.escalateAllAutomatic();
            log.info("Automatic waitlist escalation issued {} offers", issued);
        } catch (Exception e) {
            log.error("Automatic waitlist escalation failed", e);
        }
    }
}
