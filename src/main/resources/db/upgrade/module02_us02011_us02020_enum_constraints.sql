-- =====================================================================================================
-- Schema upgrade for US-02011 .. US-02020 on databases created before those stories.
--
-- Hibernate adds a CHECK constraint listing the allowed values of every @Enumerated(STRING) column when it
-- creates a table, but ddl-auto: update never rewrites an existing constraint. Two enums gained values:
--   PaymentMode      + WAIVER                   (US-02016 zero-value receipt for a full fee waiver)
--   NotificationType + ACCEPTANCE_NOTIFICATION  (US-02018 bulk acceptance notifications)
-- Without this script, inserting those values fails with "violates check constraint".
-- New columns and tables are added by Hibernate automatically; only these constraints need this script.
--
-- Safe to run repeatedly. Fresh databases created by Hibernate already have the right constraints.
-- =====================================================================================================

BEGIN;

ALTER TABLE admission_fee_deposits DROP CONSTRAINT IF EXISTS admission_fee_deposits_payment_mode_check;
ALTER TABLE admission_fee_deposits ADD CONSTRAINT admission_fee_deposits_payment_mode_check
    CHECK (payment_mode IN ('UPI', 'CARD', 'NET_BANKING', 'CASH', 'WAIVER'));

ALTER TABLE applicant_notifications DROP CONSTRAINT IF EXISTS applicant_notifications_notification_type_check;
ALTER TABLE applicant_notifications ADD CONSTRAINT applicant_notifications_notification_type_check
    CHECK (notification_type IN ('ENTRANCE_EVALUATION_REMINDER', 'ACCEPTANCE_NOTIFICATION'));

COMMIT;
