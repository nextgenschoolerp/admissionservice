package com.kvs.admission.service;

import com.kvs.core.exception.ForbiddenException;
import com.kvs.core.util.SecurityUtils;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Record-level access rules shared by the admission services.
 * Staff roles may act on any lead; an applicant parent may only act on leads registered
 * under their own guardian email.
 */
@Component
public class AdmissionAccessGuard {

    static final Set<String> STAFF_ROLES = Set.of(
            "ROLE_SUPER_ADMIN", "ROLE_ADMISSION_OFFICER", "ROLE_REGISTRAR", "ROLE_FRONT_OFFICE_COUNSELOR",
            "ROLE_ENTRANCE_EXAM_EVALUATOR", "ROLE_FINANCE_CASHIER", "ROLE_MARKETING_MANAGER", "ROLE_SCHOOL_PRINCIPAL");

    /** True when the caller holds at least one admission staff role. */
    public boolean isStaff() {
        return SecurityUtils.getCurrentUserRoles().stream().anyMatch(STAFF_ROLES::contains);
    }

    /** True when the caller's login email equals the given guardian email (case-insensitive). */
    public boolean isGuardian(String guardianEmail) {
        return guardianEmail != null && SecurityUtils.getCurrentUserEmail()
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
}
