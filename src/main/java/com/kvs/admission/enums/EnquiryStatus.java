package com.kvs.admission.enums;

/**
 * Lifecycle states of an admission enquiry (mirrors enquiry_status_enum in module2.sql).
 */
public enum EnquiryStatus {
    NEW_INQUIRY,
    CONTACTED,
    EVALUATION_SCHEDULED,
    EVALUATION_PASSED,
    OFFER_ISSUED,
    ENROLLED,
    REJECTED,
    WITHDRAWN
}
