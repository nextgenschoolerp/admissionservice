package com.kvs.admission.service;

import com.kvs.admission.entity.AuditLog;
import com.kvs.admission.repo.AuditLogRepository;
import com.kvs.core.util.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;

/**
 * Writes audit trail entries for privileged admission operations.
 * Shared by all Module 02 services so each story logs an audit entry consistently.
 */
@Service
@Slf4j
public class AdmissionAuditService {

    private static final int MAX_DETAILS_LENGTH = 1000;

    @Autowired
    private AuditLogRepository auditLogRepository;

    /**
     * Persists an audit entry. Failures are logged and never break the business transaction.
     *
     * @param resource resource identifier, e.g. {@code entrance_exam:<tenantId>}
     * @param action   action code, e.g. {@code SCHEDULE_ENTRANCE_EXAM}
     * @param outcome  SUCCESS or FAILURE
     * @param details  free-text context (truncated to 1000 characters)
     */
    public void log(String resource, String action, String outcome, String details) {
        try {
            AuditLog auditLog = new AuditLog();
            auditLog.setUserId(SecurityUtils.getCurrentUserEmail().orElse("system"));
            auditLog.setIpAddress(resolveIpAddress());
            auditLog.setAction(action);
            auditLog.setResource(resource);
            auditLog.setOutcome(outcome);
            auditLog.setEventTime(Instant.now());
            auditLog.setDetails(details != null && details.length() > MAX_DETAILS_LENGTH
                    ? details.substring(0, MAX_DETAILS_LENGTH)
                    : details);
            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.warn("Failed to write audit log for action {}: {}", action, e.getMessage());
        }
    }

    public void logSuccess(String resource, String action, String details) {
        log(resource, action, "SUCCESS", details);
    }

    private String resolveIpAddress() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            HttpServletRequest request = servletAttributes.getRequest();
            if (request.getRemoteAddr() != null) {
                return request.getRemoteAddr();
            }
        }
        // Scheduled jobs run outside an HTTP request
        return "127.0.0.1";
    }
}
