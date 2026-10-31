-- Offer notifications and entrance reminders also go to the guardian's email, from the Admissions mailbox
-- (Settings → Integrations, sent by authservice). One row per email copy: channel EMAIL, recipient_email.
ALTER TABLE applicant_notifications ADD COLUMN IF NOT EXISTS recipient_email character varying(150);
ALTER TABLE applicant_notifications DROP CONSTRAINT IF EXISTS applicant_notifications_channel_check;
ALTER TABLE applicant_notifications ADD CONSTRAINT applicant_notifications_channel_check CHECK (((channel)::text = ANY ((ARRAY['WHATSAPP'::character varying, 'EMAIL'::character varying])::text[])));
