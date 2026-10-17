package com.kvs.admission.enums;

public enum DocumentType {
    BIRTH_CERTIFICATE(true),
    TRANSFER_CERTIFICATE(true),
    AADHAAR(false),
    PREVIOUS_REPORT_CARD(false);

    /** Mandatory documents must be VERIFIED before a lead can be converted (US-02005). */
    private final boolean mandatory;

    DocumentType(boolean mandatory) {
        this.mandatory = mandatory;
    }

    public boolean isMandatory() {
        return mandatory;
    }
}
