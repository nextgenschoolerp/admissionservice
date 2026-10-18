package com.kvs.admission.enums;

/**
 * Scholarship categories for fee waivers (US-02016), each with the highest waiver percentage it allows.
 */
public enum ScholarshipCategory {
    MERIT(50),
    SIBLING(25),
    STAFF_WARD(50),
    SPORTS(50),
    ECONOMICALLY_WEAKER_SECTION(100);

    private final int maxWaiverPercent;

    ScholarshipCategory(int maxWaiverPercent) {
        this.maxWaiverPercent = maxWaiverPercent;
    }

    public int getMaxWaiverPercent() {
        return maxWaiverPercent;
    }
}
