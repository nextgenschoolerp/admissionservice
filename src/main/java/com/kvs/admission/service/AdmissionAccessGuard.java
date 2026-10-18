package com.kvs.admission.service;

import com.kvs.core.exception.ForbiddenException;
import com.kvs.core.util.SecurityUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import java.util.Set;

/**
 * Record-level access rules shared by the admission services.
 * Staff roles may act on any lead; an applicant parent may only act on leads registered
 * under their own guardian email.
 */
@Slf4j
@Component
public class AdmissionAccessGuard {

    static final Set<String> STAFF_ROLES = Set.of(
            "ROLE_SUPER_ADMIN", "ROLE_ADMISSION_OFFICER", "ROLE_REGISTRAR", "ROLE_FRONT_OFFICE_COUNSELOR",
            "ROLE_ENTRANCE_EXAM_EVALUATOR", "ROLE_FINANCE_CASHIER", "ROLE_MARKETING_MANAGER", "ROLE_SCHOOL_PRINCIPAL");

    @Autowired
    private ObjectMapper objectMapper;

    /** True when the caller holds at least one admission staff role. */
    public boolean isStaff() {
        return SecurityUtils.getCurrentUserRoles().stream().anyMatch(STAFF_ROLES::contains);
    }

    /** True when the caller is a front-office counselor. */
    public boolean isCounselor() {
        return SecurityUtils.getCurrentUserRoles().contains("ROLE_FRONT_OFFICE_COUNSELOR");
    }

    /**
     * The caller's email address. authservice tokens carry the username as the subject and the email in an
     * {@code email} claim; tokens without that claim (older tokens, tests) fall back to the subject.
     */
    public Optional<String> currentUserEmail() {
        return emailClaim().or(SecurityUtils::getCurrentUserEmail);
    }

    /** True when the caller's login email equals the given guardian email (case-insensitive). */
    public boolean isGuardian(String guardianEmail) {
        return guardianEmail != null && currentUserEmail()
                .map(email -> email.equalsIgnoreCase(guardianEmail))
                .orElse(false);
    }

    /**
     * Allows staff, or a parent acting on their own child's record.
     *
     * @throws ForbiddenException otherwise
     */
    public void requireStaffOrGuardian(String guardianEmail, String message) {
        if (!isStaff() && !isGuardian(guardianEmail)) {
            throw new ForbiddenException(message);
        }
    }

    private Optional<String> emailClaim() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return Optional.empty();
        }
        HttpServletRequest request = attributes.getRequest();
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ")) {
            return Optional.empty();
        }
        // The JwtAuthenticationFilter has already validated this token's signature and expiry; only its payload is read here
        String[] parts = header.substring(7).split("\\.");
        if (parts.length < 2) {
            return Optional.empty();
        }
        try {
            JsonNode payload = objectMapper.readTree(new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8));
            return Optional.ofNullable(payload.path("email").textValue()).filter(e -> !e.isBlank());
        } catch (Exception e) {
            log.debug("No email claim in the bearer token: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
