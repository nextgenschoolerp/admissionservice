-- =====================================================================================================
-- Seed data: Module 02 — Admission Management, Lead CRM & Student Recruitment Pipeline (admissionservice)
-- Target DB : jdbc:postgresql://localhost:5432/authservice (tables created by Hibernate from the entities)
--
-- Tenants, branches and users match the authservice seed:
--   a0eEBc99-9c0b-4ef8-bb6d-6bb9bd380a11  Delhi Public Education Society  (DPS-DELHI-NORTH, DPS-DELHI-SOUTH)
--   b1eEBc99-9c0b-4ef8-bb6d-6bb9bd380a22  Saraswati Vidya Mandir Trust     (SVKM-MUMBAI-MAIN)
--   anita.principal@dps.edu.in (PRINCIPAL), rajesh.admin@dps.edu.in (SUPER_ADMIN)
--
-- Story coverage (DPS North, Grade 6, AY 2026-27 pipeline):
--   US-02001 14 enquiries across every status and referral channel
--   US-02002 2 entrance exams (one completed, one upcoming) with 7 admit cards
--   US-02003 9 rubric evaluations with 35 criterion scores (totals = sum of criteria)
--   US-02004 6 vault documents (verified / pending / rejected)
--   US-02005 1 Student Master record (Aarav Sharma)
--   US-02006 2 offers (one accepted, one dispatched and awaiting payment)
--   US-02007 analytics are computed from the enquiries above (no table of its own)
--   US-02008 1 seat reservation deposit with receipt
--   US-02009 7 WhatsApp reminders (6 sent, 1 failed)
--   US-02010 1 merit list with 4 ranked candidates (1 excluded)
--   US-02011 5 open document follow-ups and 1 resolved
--   US-02013 sections A/B of Grade 6 (and A of Grade 1 South) with Aarav placed in 6-A
--   US-02014 Grade 6 waitlist rule (2 seats, now full): Diya offered via escalation, Kabir waiting
--   US-02015 5 counseling calls and 2 scheduled callbacks
--   US-02016 a pending SIBLING waiver for Diya and a rejected MERIT waiver for Aarav
--   US-02017 2 board lookups of transfer certificates (VALID, NOT_FOUND)
--   US-02018 2 acceptance notifications
--   US-02019 5 marketing campaigns with 10 attributed leads
--   US-02020 Advik's withdrawn lead archived with a trend snapshot
--
-- Databases created before US-02011 need db/upgrade/module02_us02011_us02020_enum_constraints.sql first.
--
-- Re-runnable: every seed UUID starts with 5eed0002-, and the script first deletes only those rows.
-- Upcoming items (the second exam, the open offer, its reminders) are relative to now(), so they
-- stay upcoming whenever the seed is applied.
-- NOTE: vault file_path values point to files that are not present on disk.
-- =====================================================================================================

BEGIN;

-- ---------------------------------------------------------------------------------------------------
-- 0. Clean previous seed rows (children first)
-- ---------------------------------------------------------------------------------------------------
DELETE FROM merit_list_entries WHERE merit_list_pk IN (SELECT id FROM merit_lists WHERE merit_list_id::text LIKE '5eed0002-%');
DELETE FROM merit_lists WHERE merit_list_id::text LIKE '5eed0002-%';
DELETE FROM evaluation_rubric_scores WHERE evaluation_pk IN (SELECT id FROM entrance_evaluations WHERE evaluation_id::text LIKE '5eed0002-%');
DELETE FROM entrance_evaluations WHERE evaluation_id::text LIKE '5eed0002-%';
DELETE FROM applicant_notifications WHERE notification_id::text LIKE '5eed0002-%';
DELETE FROM admission_fee_deposits WHERE deposit_id::text LIKE '5eed0002-%';
DELETE FROM admission_offers WHERE offer_id::text LIKE '5eed0002-%';
DELETE FROM student_master_records WHERE student_id::text LIKE '5eed0002-%';
DELETE FROM applicant_documents WHERE document_id::text LIKE '5eed0002-%';
DELETE FROM admit_cards WHERE admit_card_id::text LIKE '5eed0002-%';
DELETE FROM entrance_exam_schedules WHERE exam_id::text LIKE '5eed0002-%';
DELETE FROM admission_enquiries WHERE enquiry_id::text LIKE '5eed0002-%';
DELETE FROM archived_lead_snapshots WHERE enquiry_id::text LIKE '5eed0002-%';
DELETE FROM tc_verifications WHERE verification_id::text LIKE '5eed0002-%';
DELETE FROM fee_waivers WHERE waiver_id::text LIKE '5eed0002-%';
DELETE FROM callback_tasks WHERE callback_id::text LIKE '5eed0002-%';
DELETE FROM counseling_calls WHERE call_id::text LIKE '5eed0002-%';
DELETE FROM waitlist_entries WHERE entry_id::text LIKE '5eed0002-%';
DELETE FROM waitlist_rules WHERE created_by = 'seed:module02';
DELETE FROM grade_sections WHERE section_id::text LIKE '5eed0002-%';
DELETE FROM document_follow_ups WHERE follow_up_id::text LIKE '5eed0002-%';
DELETE FROM marketing_campaigns WHERE campaign_id::text LIKE '5eed0002-%';
DELETE FROM audit_logs WHERE created_by = 'seed:module02';

-- ---------------------------------------------------------------------------------------------------
-- 1. US-02001 Admission enquiries / leads
-- ---------------------------------------------------------------------------------------------------
INSERT INTO admission_enquiries (enquiry_id, tenant_id, branch_id, application_number, candidate_first_name, candidate_last_name,
                                 target_grade_id, academic_year_id, guardian_name, guardian_email, guardian_phone, referral_source,
                                 enquiry_status, assigned_counselor_id, remarks, created_at, updated_at, created_by, updated_by, version)
VALUES
-- DPS North, Grade 6 — full pipeline
('5eed0002-0001-4000-8000-000000000001', 'a0eEBc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'DPS-DELHI-NORTH', 'APP-2026-3F9A1C01', 'Aarav', 'Sharma',
 'GRADE-06', 'AY-2026-27', 'Rohit Sharma', 'rohit.sharma@gmail.com', '+919810012301', 'WALK_IN',
 'ENROLLED', 'meera.counselor@dps.edu.in', 'Elder sibling studies in Grade 9 at DPS North; parents prefer the science stream later.',
 '2026-08-04 05:10:00+00', '2026-10-05 06:45:00+00', 'meera.counselor@dps.edu.in', 'rohit.sharma@gmail.com', 0),
('5eed0002-0001-4000-8000-000000000002', 'a0eEBc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'DPS-DELHI-NORTH', 'APP-2026-3F9A1C02', 'Diya', 'Patel',
 'GRADE-06', 'AY-2026-27', 'Nikhil Patel', 'nikhil.patel@yahoo.co.in', '+919810012302', 'REFERRAL',
 'OFFER_ISSUED', 'meera.counselor@dps.edu.in', 'Referred by an existing DPS parent (Grade 4). Interested in the school orchestra.',
 '2026-08-06 07:25:00+00', '2026-10-10 08:00:00+00', 'meera.counselor@dps.edu.in', 'ramesh.accounts@dps.edu.in', 0),
('5eed0002-0001-4000-8000-000000000003', 'a0eEBc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'DPS-DELHI-NORTH', 'APP-2026-3F9A1C03', 'Kabir', 'Singh',
 'GRADE-06', 'AY-2026-27', 'Harpreet Singh', 'harpreet.singh@outlook.com', '+919810012303', 'WEBSITE',
 'EVALUATION_PASSED', 'meera.counselor@dps.edu.in', 'Enquiry submitted through the admissions web form; requested school bus from Rohini Sector 9.',
 '2026-08-11 10:40:00+00', '2026-09-22 09:15:00+00', 'meera.counselor@dps.edu.in', 'suresh.registrar@dps.edu.in', 0),
('5eed0002-0001-4000-8000-000000000004', 'a0eEBc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'DPS-DELHI-NORTH', 'APP-2026-3F9A1C04', 'Ananya', 'Iyer',
 'GRADE-06', 'AY-2026-27', 'Lakshmi Iyer', 'lakshmi.iyer@gmail.com', '+919810012304', 'SOCIAL_MEDIA',
 'EVALUATION_SCHEDULED', 'meera.counselor@dps.edu.in', 'Responded to the Instagram open-house campaign. Interview to be re-taken after counselling.',
 '2026-08-14 06:05:00+00', '2026-09-22 10:30:00+00', 'meera.counselor@dps.edu.in', 'suresh.registrar@dps.edu.in', 0),
('5eed0002-0001-4000-8000-000000000005', 'a0eEBc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'DPS-DELHI-NORTH', 'APP-2026-3F9A1C05', 'Vihaan', 'Gupta',
 'GRADE-06', 'AY-2026-27', 'Amit Gupta', 'amit.gupta@rediffmail.com', '+919810012305', 'PHONE',
 'REJECTED', 'meera.counselor@dps.edu.in', 'Entrance test score below the passing mark; family informed and advised to re-apply next year.',
 '2026-08-18 11:20:00+00', '2026-09-23 05:00:00+00', 'meera.counselor@dps.edu.in', 'deepak.admissions@dps.edu.in', 0),
('5eed0002-0001-4000-8000-000000000006', 'a0eEBc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'DPS-DELHI-NORTH', 'APP-2026-3F9A1C06', 'Ishaan', 'Reddy',
 'GRADE-06', 'AY-2026-27', 'Srinivas Reddy', 'srinivas.reddy@gmail.com', '+919810012306', 'WALK_IN',
 'EVALUATION_SCHEDULED', 'meera.counselor@dps.edu.in', 'Late-cycle walk-in; scheduled for the second entrance test round.',
 '2026-09-29 04:50:00+00', '2026-10-08 06:00:00+00', 'meera.counselor@dps.edu.in', 'deepak.admissions@dps.edu.in', 0),
('5eed0002-0001-4000-8000-000000000007', 'a0eEBc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'DPS-DELHI-NORTH', 'APP-2026-3F9A1C07', 'Saanvi', 'Nair',
 'GRADE-06', 'AY-2026-27', 'Priya Nair', 'priya.nair@gmail.com', '+919810012307', 'WHATSAPP',
 'EVALUATION_SCHEDULED', 'meera.counselor@dps.edu.in', 'Lead came in through the WhatsApp business number; guardian prefers calls after 6 pm.',
 '2026-10-01 12:35:00+00', '2026-10-08 06:00:00+00', 'meera.counselor@dps.edu.in', 'deepak.admissions@dps.edu.in', 0),
('5eed0002-0001-4000-8000-000000000008', 'a0eEBc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'DPS-DELHI-NORTH', 'APP-2026-3F9A1C08', 'Myra', 'Kapoor',
 'GRADE-06', 'AY-2026-27', 'Sunil Kapoor', 'sunil.kapoor@gmail.com', '+919810012308', 'WEBSITE',
 'NEW_INQUIRY', 'meera.counselor@dps.edu.in', 'Downloaded the fee structure brochure; first call pending.',
 '2026-10-12 09:00:00+00', '2026-10-12 09:00:00+00', 'meera.counselor@dps.edu.in', NULL, 0),
('5eed0002-0001-4000-8000-000000000009', 'a0eEBc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'DPS-DELHI-NORTH', 'APP-2026-3F9A1C09', 'Reyansh', 'Joshi',
 'GRADE-06', 'AY-2026-27', 'Kavita Joshi', 'kavita.joshi@gmail.com', '+919810012309', 'PHONE',
 'CONTACTED', 'meera.counselor@dps.edu.in', 'Campus tour booked for Saturday 10 am; mother wants details on the sports academy.',
 '2026-10-09 07:45:00+00', '2026-10-13 05:30:00+00', 'meera.counselor@dps.edu.in', 'meera.counselor@dps.edu.in', 0),
('5eed0002-0001-4000-8000-000000000010', 'a0eEBc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'DPS-DELHI-NORTH', 'APP-2026-3F9A1C10', 'Advik', 'Menon',
 'GRADE-06', 'AY-2026-27', 'Ravi Menon', 'ravi.menon@gmail.com', '+919810012310', 'REFERRAL',
 'WITHDRAWN', 'meera.counselor@dps.edu.in', 'Withdrawn: family relocating to Bengaluru in December.',
 '2026-08-20 08:15:00+00', '2026-09-02 10:00:00+00', 'meera.counselor@dps.edu.in', 'meera.counselor@dps.edu.in', 0),
-- DPS RK Puram (South), Grade 1 — early funnel
('5eed0002-0001-4000-8000-000000000011', 'a0eEBc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'DPS-DELHI-SOUTH', 'APP-2026-3F9A1C11', 'Aadhya', 'Verma',
 'GRADE-01', 'AY-2026-27', 'Sanjay Verma', 'sanjay.verma@gmail.com', '+919810012311', 'WALK_IN',
 'NEW_INQUIRY', 'meera.counselor@dps.edu.in', 'Grade 1 admission under the neighbourhood criteria (lives 1.2 km from campus).',
 '2026-10-14 05:20:00+00', '2026-10-14 05:20:00+00', 'meera.counselor@dps.edu.in', NULL, 0),
('5eed0002-0001-4000-8000-000000000012', 'a0eEBc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'DPS-DELHI-SOUTH', 'APP-2026-3F9A1C12', 'Arjun', 'Malhotra',
 'GRADE-01', 'AY-2026-27', 'Neha Malhotra', 'neha.malhotra@gmail.com', '+919810012312', 'WEBSITE',
 'CONTACTED', 'meera.counselor@dps.edu.in', 'Parent interaction slot shared by email; awaiting confirmation.',
 '2026-10-07 11:10:00+00', '2026-10-11 06:40:00+00', 'meera.counselor@dps.edu.in', 'meera.counselor@dps.edu.in', 0),
-- SVKM Mumbai, Grade 9 — second tenant
('5eed0002-0001-4000-8000-000000000013', 'b1eEBc99-9c0b-4ef8-bb6d-6bb9bd380a22', 'SVKM-MUMBAI-MAIN', 'APP-2026-7C2E4D13', 'Riya', 'Deshmukh',
 'GRADE-09', 'AY-2026-27', 'Prakash Deshmukh', 'prakash.deshmukh@gmail.com', '+919820045613', 'REFERRAL',
 'NEW_INQUIRY', 'farah.counselor@svkm.org', 'Transferring from a state-board school in Pune; needs ICSE bridge-course details.',
 '2026-10-10 06:30:00+00', '2026-10-10 06:30:00+00', 'farah.counselor@svkm.org', NULL, 0),
('5eed0002-0001-4000-8000-000000000014', 'b1eEBc99-9c0b-4ef8-bb6d-6bb9bd380a22', 'SVKM-MUMBAI-MAIN', 'APP-2026-7C2E4D14', 'Om', 'Kulkarni',
 'GRADE-09', 'AY-2026-27', 'Swati Kulkarni', 'swati.kulkarni@gmail.com', '+919820045614', 'WALK_IN',
 'CONTACTED', 'farah.counselor@svkm.org', 'Visited with report cards of Grades 7 and 8; follow-up call done.',
 '2026-10-03 09:55:00+00', '2026-10-06 07:15:00+00', 'farah.counselor@svkm.org', 'farah.counselor@svkm.org', 0);

-- ---------------------------------------------------------------------------------------------------
-- 2. US-02002 Entrance exams and admit cards
-- ---------------------------------------------------------------------------------------------------
INSERT INTO entrance_exam_schedules (exam_id, tenant_id, branch_id, exam_code, exam_title, academic_year_id, target_grade_id,
                                     exam_start_at, duration_minutes, venue, max_marks, passing_marks, reporting_instructions,
                                     status, created_at, updated_at, created_by, updated_by, version)
VALUES
('5eed0002-0002-4000-8000-000000000001', 'a0eEBc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'DPS-DELHI-NORTH', 'EXAM-2026-A1B2C3D4',
 'Grade 6 Entrance Test 2026-27 (Round 1)', 'AY-2026-27', 'GRADE-06',
 '2026-09-20 03:30:00+00', 90, 'Examination Hall, Block A, DPS North Campus', 100.00, 50.00,
 'Report by 8:30 am with the printed admit card, two HB pencils and a transparent water bottle.',
 'COMPLETED', '2026-09-01 06:00:00+00', '2026-09-20 05:30:00+00', 'deepak.admissions@dps.edu.in', 'deepak.admissions@dps.edu.in', 0),
('5eed0002-0002-4000-8000-000000000002', 'a0eEBc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'DPS-DELHI-NORTH', 'EXAM-2026-E5F60718',
 'Grade 6 Entrance Test 2026-27 (Round 2)', 'AY-2026-27', 'GRADE-06',
 (date_trunc('day', now() AT TIME ZONE 'Asia/Kolkata') + interval '2 days 9 hours') AT TIME ZONE 'Asia/Kolkata', 90, 'Seminar Room 2, Block C, DPS North Campus', 100.00, 50.00,
 'Report by 8:30 am. Parents may wait in the visitors lounge near Gate 2.',
 'SCHEDULED', '2026-10-08 06:00:00+00', '2026-10-08 06:00:00+00', 'deepak.admissions@dps.edu.in', NULL, 0);

INSERT INTO admit_cards (admit_card_id, tenant_id, exam_id, enquiry_id, admit_card_number, roll_number, seat_number, candidate_name,
                         status, created_at, updated_at, created_by, updated_by, version)
VALUES
('5eed0002-0003-4000-8000-000000000001', 'a0eEBc99-9c0b-4ef8-bb6d-6bb9bd380a11', '5eed0002-0002-4000-8000-000000000001', '5eed0002-0001-4000-8000-000000000001', 'ADMIT-2026-9C41A001', 'EXAM-2026-A1B2C3D4-001', 'S-001', 'Aarav Sharma',  'ISSUED', '2026-09-01 06:00:00+00', '2026-09-01 06:00:00+00', 'deepak.admissions@dps.edu.in', NULL, 0),
('5eed0002-0003-4000-8000-000000000002', 'a0eEBc99-9c0b-4ef8-bb6d-6bb9bd380a11', '5eed0002-0002-4000-8000-000000000001', '5eed0002-0001-4000-8000-000000000002', 'ADMIT-2026-9C41A002', 'EXAM-2026-A1B2C3D4-002', 'S-002', 'Diya Patel',    'ISSUED', '2026-09-01 06:00:00+00', '2026-09-01 06:00:00+00', 'deepak.admissions@dps.edu.in', NULL, 0),
('5eed0002-0003-4000-8000-000000000003', 'a0eEBc99-9c0b-4ef8-bb6d-6bb9bd380a11', '5eed0002-0002-4000-8000-000000000001', '5eed0002-0001-4000-8000-000000000003', 'ADMIT-2026-9C41A003', 'EXAM-2026-A1B2C3D4-003', 'S-003', 'Kabir Singh',   'ISSUED', '2026-09-01 06:00:00+00', '2026-09-01 06:00:00+00', 'deepak.admissions@dps.edu.in', NULL, 0),
('5eed0002-0003-4000-8000-000000000004', 'a0eEBc99-9c0b-4ef8-bb6d-6bb9bd380a11', '5eed0002-0002-4000-8000-000000000001', '5eed0002-0001-4000-8000-000000000004', 'ADMIT-2026-9C41A004', 'EXAM-2026-A1B2C3D4-004', 'S-004', 'Ananya Iyer',   'ISSUED', '2026-09-01 06:00:00+00', '2026-09-01 06:00:00+00', 'deepak.admissions@dps.edu.in', NULL, 0),
('5eed0002-0003-4000-8000-000000000005', 'a0eEBc99-9c0b-4ef8-bb6d-6bb9bd380a11', '5eed0002-0002-4000-8000-000000000001', '5eed0002-0001-4000-8000-000000000005', 'ADMIT-2026-9C41A005', 'EXAM-2026-A1B2C3D4-005', 'S-005', 'Vihaan Gupta',  'ISSUED', '2026-09-01 06:00:00+00', '2026-09-01 06:00:00+00', 'deepak.admissions@dps.edu.in', NULL, 0),
('5eed0002-0003-4000-8000-000000000006', 'a0eEBc99-9c0b-4ef8-bb6d-6bb9bd380a11', '5eed0002-0002-4000-8000-000000000002', '5eed0002-0001-4000-8000-000000000006', 'ADMIT-2026-B7D2E006', 'EXAM-2026-E5F60718-001', 'S-001', 'Ishaan Reddy',  'ISSUED', '2026-10-08 06:00:00+00', '2026-10-08 06:00:00+00', 'deepak.admissions@dps.edu.in', NULL, 0),
('5eed0002-0003-4000-8000-000000000007', 'a0eEBc99-9c0b-4ef8-bb6d-6bb9bd380a11', '5eed0002-0002-4000-8000-000000000002', '5eed0002-0001-4000-8000-000000000007', 'ADMIT-2026-B7D2E007', 'EXAM-2026-E5F60718-002', 'S-002', 'Saanvi Nair',   'ISSUED', '2026-10-08 06:00:00+00', '2026-10-08 06:00:00+00', 'deepak.admissions@dps.edu.in', NULL, 0);

-- ---------------------------------------------------------------------------------------------------
-- 3. US-02003 Rubric evaluations (ENTRANCE_TEST: MATHEMATICS 40, LANGUAGE 30, LOGICAL_REASONING 30;
--    INTERVIEW: COMMUNICATION 20, ACADEMIC_APTITUDE 30, GENERAL_AWARENESS 20, CONFIDENCE_AND_CONDUCT 15, PARENT_INTERACTION 15)
-- ---------------------------------------------------------------------------------------------------
INSERT INTO entrance_evaluations (evaluation_id, tenant_id, enquiry_id, evaluation_type, evaluation_date, examiner_id, total_score,
                                  passing_score, evaluation_status, feedback_notes, created_at, updated_at, created_by, updated_by, version)
VALUES
('5eed0002-0004-4000-8000-000000000001', 'a0eEBc99-9c0b-4ef8-bb6d-6bb9bd380a11', '5eed0002-0001-4000-8000-000000000001', 'ENTRANCE_TEST', '2026-09-20 05:00:00+00', 'kavya.evaluator@dps.edu.in', 88.00, 50.00, 'PASSED',
 'Excellent number sense; completed the reasoning section with time to spare.', '2026-09-21 09:00:00+00', '2026-09-21 09:00:00+00', 'suresh.registrar@dps.edu.in', NULL, 0),
('5eed0002-0004-4000-8000-000000000002', 'a0eEBc99-9c0b-4ef8-bb6d-6bb9bd380a11', '5eed0002-0001-4000-8000-000000000001', 'INTERVIEW', '2026-09-22 04:30:00+00', 'suresh.registrar@dps.edu.in', 84.00, 50.00, 'PASSED',
 'Confident, articulate; spoke well about his robotics club project.', '2026-09-22 06:00:00+00', '2026-09-22 06:00:00+00', 'suresh.registrar@dps.edu.in', NULL, 0),
('5eed0002-0004-4000-8000-000000000003', 'a0eEBc99-9c0b-4ef8-bb6d-6bb9bd380a11', '5eed0002-0001-4000-8000-000000000002', 'ENTRANCE_TEST', '2026-09-20 05:00:00+00', 'kavya.evaluator@dps.edu.in', 79.00, 50.00, 'PASSED',
 'Strong language skills; minor errors in fractions.', '2026-09-21 09:05:00+00', '2026-09-21 09:05:00+00', 'suresh.registrar@dps.edu.in', NULL, 0),
('5eed0002-0004-4000-8000-000000000004', 'a0eEBc99-9c0b-4ef8-bb6d-6bb9bd380a11', '5eed0002-0001-4000-8000-000000000002', 'INTERVIEW', '2026-09-22 05:00:00+00', 'suresh.registrar@dps.edu.in', 81.00, 50.00, 'PASSED',
 'Warm and expressive; parents engaged and well informed about school values.', '2026-09-22 06:30:00+00', '2026-09-22 06:30:00+00', 'suresh.registrar@dps.edu.in', NULL, 0),
('5eed0002-0004-4000-8000-000000000005', 'a0eEBc99-9c0b-4ef8-bb6d-6bb9bd380a11', '5eed0002-0001-4000-8000-000000000003', 'ENTRANCE_TEST', '2026-09-20 05:00:00+00', 'kavya.evaluator@dps.edu.in', 72.00, 50.00, 'PASSED',
 'Consistent across sections; reading comprehension can improve.', '2026-09-21 09:10:00+00', '2026-09-21 09:10:00+00', 'suresh.registrar@dps.edu.in', NULL, 0),
('5eed0002-0004-4000-8000-000000000006', 'a0eEBc99-9c0b-4ef8-bb6d-6bb9bd380a11', '5eed0002-0001-4000-8000-000000000003', 'INTERVIEW', '2026-09-22 05:30:00+00', 'suresh.registrar@dps.edu.in', 68.00, 50.00, 'PASSED',
 'Initially shy but opened up when discussing cricket; good general awareness.', '2026-09-22 07:00:00+00', '2026-09-22 07:00:00+00', 'suresh.registrar@dps.edu.in', NULL, 0),
('5eed0002-0004-4000-8000-000000000007', 'a0eEBc99-9c0b-4ef8-bb6d-6bb9bd380a11', '5eed0002-0001-4000-8000-000000000004', 'ENTRANCE_TEST', '2026-09-20 05:00:00+00', 'kavya.evaluator@dps.edu.in', 65.00, 50.00, 'PASSED',
 'Adequate performance; logical reasoning needs practice.', '2026-09-21 09:15:00+00', '2026-09-21 09:15:00+00', 'suresh.registrar@dps.edu.in', NULL, 0),
('5eed0002-0004-4000-8000-000000000008', 'a0eEBc99-9c0b-4ef8-bb6d-6bb9bd380a11', '5eed0002-0001-4000-8000-000000000004', 'INTERVIEW', '2026-09-22 06:00:00+00', 'suresh.registrar@dps.edu.in', 38.00, 50.00, 'FAILED',
 'Very anxious during the interaction and gave minimal answers; recommend a re-interview after a counselling session.', '2026-09-22 10:30:00+00', '2026-09-22 10:30:00+00', 'suresh.registrar@dps.edu.in', NULL, 0),
('5eed0002-0004-4000-8000-000000000009', 'a0eEBc99-9c0b-4ef8-bb6d-6bb9bd380a11', '5eed0002-0001-4000-8000-000000000005', 'ENTRANCE_TEST', '2026-09-20 05:00:00+00', 'kavya.evaluator@dps.edu.in', 31.00, 50.00, 'FAILED',
 'Struggled with multi-step word problems and the comprehension passage.', '2026-09-21 09:20:00+00', '2026-09-21 09:20:00+00', 'suresh.registrar@dps.edu.in', NULL, 0);

INSERT INTO evaluation_rubric_scores (evaluation_pk, criterion, max_score, awarded_score, remarks)
SELECT e.id, s.criterion, s.max_score, s.awarded_score, s.remarks
FROM (VALUES
    ('5eed0002-0004-4000-8000-000000000001', 'MATHEMATICS',            40, 34, 'Accurate with fractions and geometry'),
    ('5eed0002-0004-4000-8000-000000000001', 'LANGUAGE',               30, 27, 'Rich vocabulary in the essay'),
    ('5eed0002-0004-4000-8000-000000000001', 'LOGICAL_REASONING',      30, 27, 'Solved all pattern puzzles'),
    ('5eed0002-0004-4000-8000-000000000002', 'COMMUNICATION',          20, 17, 'Clear and well structured answers'),
    ('5eed0002-0004-4000-8000-000000000002', 'ACADEMIC_APTITUDE',      30, 26, 'Explained his science project in depth'),
    ('5eed0002-0004-4000-8000-000000000002', 'GENERAL_AWARENESS',      20, 17, 'Follows current events'),
    ('5eed0002-0004-4000-8000-000000000002', 'CONFIDENCE_AND_CONDUCT', 15, 12, 'Polite and composed'),
    ('5eed0002-0004-4000-8000-000000000002', 'PARENT_INTERACTION',     15, 12, 'Parents supportive and engaged'),
    ('5eed0002-0004-4000-8000-000000000003', 'MATHEMATICS',            40, 31, 'Minor slips in fractions'),
    ('5eed0002-0004-4000-8000-000000000003', 'LANGUAGE',               30, 25, 'Fluent and expressive writing'),
    ('5eed0002-0004-4000-8000-000000000003', 'LOGICAL_REASONING',      30, 23, NULL),
    ('5eed0002-0004-4000-8000-000000000004', 'COMMUNICATION',          20, 16, 'Expressive storyteller'),
    ('5eed0002-0004-4000-8000-000000000004', 'ACADEMIC_APTITUDE',      30, 25, NULL),
    ('5eed0002-0004-4000-8000-000000000004', 'GENERAL_AWARENESS',      20, 16, NULL),
    ('5eed0002-0004-4000-8000-000000000004', 'CONFIDENCE_AND_CONDUCT', 15, 12, NULL),
    ('5eed0002-0004-4000-8000-000000000004', 'PARENT_INTERACTION',     15, 12, 'Parents aligned with school values'),
    ('5eed0002-0004-4000-8000-000000000005', 'MATHEMATICS',            40, 28, NULL),
    ('5eed0002-0004-4000-8000-000000000005', 'LANGUAGE',               30, 22, 'Comprehension answers were brief'),
    ('5eed0002-0004-4000-8000-000000000005', 'LOGICAL_REASONING',      30, 22, NULL),
    ('5eed0002-0004-4000-8000-000000000006', 'COMMUNICATION',          20, 14, 'Short answers at first'),
    ('5eed0002-0004-4000-8000-000000000006', 'ACADEMIC_APTITUDE',      30, 20, NULL),
    ('5eed0002-0004-4000-8000-000000000006', 'GENERAL_AWARENESS',      20, 14, 'Good sports knowledge'),
    ('5eed0002-0004-4000-8000-000000000006', 'CONFIDENCE_AND_CONDUCT', 15, 10, NULL),
    ('5eed0002-0004-4000-8000-000000000006', 'PARENT_INTERACTION',     15, 10, NULL),
    ('5eed0002-0004-4000-8000-000000000007', 'MATHEMATICS',            40, 26, NULL),
    ('5eed0002-0004-4000-8000-000000000007', 'LANGUAGE',               30, 20, NULL),
    ('5eed0002-0004-4000-8000-000000000007', 'LOGICAL_REASONING',      30, 19, 'Needs practice with sequences'),
    ('5eed0002-0004-4000-8000-000000000008', 'COMMUNICATION',          20,  8, 'Mostly one-word answers'),
    ('5eed0002-0004-4000-8000-000000000008', 'ACADEMIC_APTITUDE',      30, 12, 'Could not explain her favourite subject'),
    ('5eed0002-0004-4000-8000-000000000008', 'GENERAL_AWARENESS',      20,  8, NULL),
    ('5eed0002-0004-4000-8000-000000000008', 'CONFIDENCE_AND_CONDUCT', 15,  5, 'Visibly anxious'),
    ('5eed0002-0004-4000-8000-000000000008', 'PARENT_INTERACTION',     15,  5, 'Parent answered on the child''s behalf'),
    ('5eed0002-0004-4000-8000-000000000009', 'MATHEMATICS',            40, 12, 'Could not complete multi-step problems'),
    ('5eed0002-0004-4000-8000-000000000009', 'LANGUAGE',               30, 10, NULL),
    ('5eed0002-0004-4000-8000-000000000009', 'LOGICAL_REASONING',      30,  9, NULL)
) AS s(evaluation_id, criterion, max_score, awarded_score, remarks)
JOIN entrance_evaluations e ON e.evaluation_id = s.evaluation_id::uuid;

-- ---------------------------------------------------------------------------------------------------
-- 4. US-02010 Merit list (weights 0.7 entrance / 0.3 interview). Vihaan is excluded (no interview, rejected).
-- ---------------------------------------------------------------------------------------------------
INSERT INTO merit_lists (merit_list_id, merit_list_code, tenant_id, branch_id, academic_year_id, target_grade_id, entrance_test_weight,
                         interview_weight, ranked_candidates, excluded_candidates, generated_by, created_at, updated_at, created_by, updated_by, version)
VALUES ('5eed0002-000a-4000-8000-000000000001', 'MERIT-2026-4D8E21F0', 'a0eEBc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'DPS-DELHI-NORTH', 'AY-2026-27', 'GRADE-06',
        0.700, 0.300, 4, 1, 'deepak.admissions@dps.edu.in', '2026-09-25 07:00:00+00', '2026-09-25 07:00:00+00', 'deepak.admissions@dps.edu.in', NULL, 0);

INSERT INTO merit_list_entries (merit_list_pk, enquiry_id, application_number, candidate_name, entrance_test_score, interview_score,
                                weighted_score, merit_rank, qualified)
SELECT m.id, v.enquiry_id::uuid, v.application_number, v.candidate_name, v.entrance, v.interview, v.weighted, v.merit_rank, v.qualified
FROM (VALUES
    ('5eed0002-0001-4000-8000-000000000001', 'APP-2026-3F9A1C01', 'Aarav Sharma', 88.00, 84.00, 86.80, 1, true),
    ('5eed0002-0001-4000-8000-000000000002', 'APP-2026-3F9A1C02', 'Diya Patel',   79.00, 81.00, 79.60, 2, true),
    ('5eed0002-0001-4000-8000-000000000003', 'APP-2026-3F9A1C03', 'Kabir Singh',  72.00, 68.00, 70.80, 3, true),
    ('5eed0002-0001-4000-8000-000000000004', 'APP-2026-3F9A1C04', 'Ananya Iyer',  65.00, 38.00, 56.90, 4, false)
) AS v(enquiry_id, application_number, candidate_name, entrance, interview, weighted, merit_rank, qualified)
CROSS JOIN merit_lists m
WHERE m.merit_list_id = '5eed0002-000a-4000-8000-000000000001';

-- ---------------------------------------------------------------------------------------------------
-- 5. US-02004 Document vault (files live under <vault>/<tenant>/<enquiryId>/<uuid>.<ext>)
-- ---------------------------------------------------------------------------------------------------
INSERT INTO applicant_documents (document_id, tenant_id, enquiry_id, document_type, original_file_name, file_path, content_type,
                                 file_size_bytes, checksum_sha256, verification_status, uploaded_by, verified_by_user_id, verified_at,
                                 verification_notes, created_at, updated_at, created_by, updated_by, version)
SELECT d.document_id::uuid, 'a0eEBc99-9c0b-4ef8-bb6d-6bb9bd380a11', d.enquiry_id::uuid, d.document_type, d.original_file_name,
       'a0eEBc99-9c0b-4ef8-bb6d-6bb9bd380a11/' || d.enquiry_id || '/' || d.document_id || d.ext,
       d.content_type, d.size_bytes, encode(sha256(convert_to('seed:' || d.document_id, 'UTF8')), 'hex'),
       d.status, 'kavya.evaluator@dps.edu.in', d.verified_by, d.verified_at::timestamptz, d.notes,
       d.created_at::timestamptz, COALESCE(d.verified_at, d.created_at)::timestamptz, 'kavya.evaluator@dps.edu.in', d.verified_by, 0
FROM (VALUES
    ('5eed0002-0005-4000-8000-000000000001', '5eed0002-0001-4000-8000-000000000001', 'BIRTH_CERTIFICATE',    'Aarav_Birth_Certificate_MCD.pdf', '.pdf', 'application/pdf', 284311,
     'VERIFIED', 'suresh.registrar@dps.edu.in', '2026-09-24 06:10:00+00', 'Original MCD certificate sighted at the front office.', '2026-09-23 05:40:00+00'),
    ('5eed0002-0005-4000-8000-000000000002', '5eed0002-0001-4000-8000-000000000001', 'TRANSFER_CERTIFICATE', 'Aarav_TC_StMarys_2026.pdf',       '.pdf', 'application/pdf', 412907,
     'VERIFIED', 'suresh.registrar@dps.edu.in', '2026-09-24 06:15:00+00', 'Signed and stamped by the previous school principal.', '2026-09-23 05:45:00+00'),
    ('5eed0002-0005-4000-8000-000000000003', '5eed0002-0001-4000-8000-000000000001', 'AADHAAR',              'Aarav_Aadhaar_masked.png',        '.png', 'image/png',       198452,
     'VERIFIED', 'suresh.registrar@dps.edu.in', '2026-09-24 06:20:00+00', 'Masked Aadhaar; name and DOB match the birth certificate.', '2026-09-23 05:50:00+00'),
    ('5eed0002-0005-4000-8000-000000000004', '5eed0002-0001-4000-8000-000000000002', 'BIRTH_CERTIFICATE',    'Diya_Patel_Birth_Certificate.pdf','.pdf', 'application/pdf', 301776,
     'VERIFIED', 'suresh.registrar@dps.edu.in', '2026-10-06 07:00:00+00', 'Verified against the original.', '2026-10-04 09:30:00+00'),
    ('5eed0002-0005-4000-8000-000000000005', '5eed0002-0001-4000-8000-000000000002', 'TRANSFER_CERTIFICATE', 'Diya_TC_scan.jpg',                '.jpg', 'image/jpeg',      856120,
     'PENDING', NULL, NULL, NULL, '2026-10-11 12:15:00+00'),
    ('5eed0002-0005-4000-8000-000000000006', '5eed0002-0001-4000-8000-000000000003', 'TRANSFER_CERTIFICATE', 'Kabir_TC.pdf',                    '.pdf', 'application/pdf', 220034,
     'REJECTED', 'suresh.registrar@dps.edu.in', '2026-09-26 08:40:00+00', 'Not signed by the previous school principal; please re-upload a signed copy.', '2026-09-25 10:05:00+00')
) AS d(document_id, enquiry_id, document_type, original_file_name, ext, content_type, size_bytes, status, verified_by, verified_at, notes, created_at);

-- ---------------------------------------------------------------------------------------------------
-- 6. US-02006 Offer letters with payment links
-- ---------------------------------------------------------------------------------------------------
INSERT INTO admission_offers (offer_id, tenant_id, enquiry_id, offer_code, offered_grade_id, academic_year_id, offer_status, valid_until_date,
                              initial_fee_amount, currency, payment_link_token, payment_link_url, letter_content, dispatched_at, accepted_at,
                              created_at, updated_at, created_by, updated_by, version)
SELECT o.offer_id::uuid, 'a0eEBc99-9c0b-4ef8-bb6d-6bb9bd380a11', o.enquiry_id::uuid, o.offer_code, 'GRADE-06', 'AY-2026-27', o.status, o.valid_until,
       25000.00, 'INR', o.token,
       'https://pay.kvs.local/admissions/offers?offer=' || o.offer_code || '&token=' || o.token,
       'Offer Reference: ' || o.offer_code || E'\nDate: ' || to_char(o.dispatched_at, 'DD Mon YYYY') || E'\n\n'
         || 'Dear ' || o.guardian || E',\n\nWe are pleased to offer admission to ' || o.candidate || ' (Application ' || o.application || ') '
         || E'for grade GRADE-06 in academic year AY-2026-27.\n\n'
         || 'To confirm the seat, please pay the seat reservation fee of INR 25000.00 on or before ' || to_char(o.valid_until, 'DD Mon YYYY')
         || E' using the secure link below:\n' || 'https://pay.kvs.local/admissions/offers?offer=' || o.offer_code || '&token=' || o.token
         || E'\n\nThe offer lapses automatically if the fee is not received by the due date.\n\nAdmissions Office',
       o.dispatched_at, o.accepted_at, o.dispatched_at, COALESCE(o.accepted_at, o.dispatched_at), 'ramesh.accounts@dps.edu.in', o.updated_by, 0
FROM (VALUES
    ('5eed0002-0006-4000-8000-000000000001', '5eed0002-0001-4000-8000-000000000001', 'OFFER-2026-6B3F9A01', 'ACCEPTED', DATE '2026-10-12',
     'Xq3vR8sLmP2nT6wYc9KbHd4FjA7uZe1GoV5iN0rQsWt', 'Rohit Sharma', 'Aarav Sharma', 'APP-2026-3F9A1C01',
     TIMESTAMPTZ '2026-09-28 06:00:00+00', TIMESTAMPTZ '2026-10-03 11:20:00+00', 'anita.principal@dps.edu.in'),
    ('5eed0002-0006-4000-8000-000000000002', '5eed0002-0001-4000-8000-000000000002', 'OFFER-2026-6B3F9A02', 'DISPATCHED', CURRENT_DATE + 14,
     'Lm8Tq2Wz5Rc1Yv7Nb4Hs0Kd9Fg3Ja6Pu2Xe8Ci5Vo1Rt', 'Nikhil Patel', 'Diya Patel', 'APP-2026-3F9A1C02',
     TIMESTAMPTZ '2026-10-10 08:00:00+00', NULL::timestamptz, NULL)
) AS o(offer_id, enquiry_id, offer_code, status, valid_until, token, guardian, candidate, application, dispatched_at, accepted_at, updated_by);

-- ---------------------------------------------------------------------------------------------------
-- 7. US-02008 Seat reservation deposit and digital receipt
-- ---------------------------------------------------------------------------------------------------
INSERT INTO admission_fee_deposits (deposit_id, tenant_id, offer_id, enquiry_id, receipt_number, amount_paid, currency, payment_mode,
                                    transaction_reference, payment_timestamp, collected_by, remarks, created_at, updated_at, created_by, updated_by, version)
VALUES ('5eed0002-0007-4000-8000-000000000001', 'a0eEBc99-9c0b-4ef8-bb6d-6bb9bd380a11', '5eed0002-0006-4000-8000-000000000001',
        '5eed0002-0001-4000-8000-000000000001', 'RCPT-2026-C2A7E301', 25000.00, 'INR', 'UPI', 'UPI-424242987612',
        '2026-10-03 11:20:00+00', 'anita.principal@dps.edu.in', 'Paid by father via PhonePe at the admissions counter.',
        '2026-10-03 11:20:00+00', '2026-10-03 11:20:00+00', 'anita.principal@dps.edu.in', NULL, 0);

-- ---------------------------------------------------------------------------------------------------
-- 8. US-02005 Student Master record (converted after verified documents and paid deposit)
-- ---------------------------------------------------------------------------------------------------
INSERT INTO student_master_records (student_id, tenant_id, branch_id, enquiry_id, admission_number, first_name, last_name, date_of_birth,
                                    gender, grade_id, academic_year_id, guardian_name, guardian_email, guardian_phone, status, converted_by,
                                    created_at, updated_at, created_by, updated_by, version)
VALUES ('5eed0002-0008-4000-8000-000000000001', 'a0eEBc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'DPS-DELHI-NORTH', '5eed0002-0001-4000-8000-000000000001',
        'ADM-2026-0F5D8B21', 'Aarav', 'Sharma', DATE '2015-03-14', 'MALE', 'GRADE-06', 'AY-2026-27',
        'Rohit Sharma', 'rohit.sharma@gmail.com', '+919810012301', 'ACTIVE', 'rohit.sharma@gmail.com',
        '2026-10-05 06:45:00+00', '2026-10-05 06:45:00+00', 'rohit.sharma@gmail.com', NULL, 0);

-- ---------------------------------------------------------------------------------------------------
-- 9. US-02009 WhatsApp reminders (round 1 sent the day before; round 2 sent an hour ago, one failed)
-- ---------------------------------------------------------------------------------------------------
INSERT INTO applicant_notifications (notification_id, tenant_id, enquiry_id, exam_id, admit_card_id, channel, notification_type, recipient_phone,
                                     message_body, status, provider_message_id, failure_reason, dispatched_at, created_at, updated_at, created_by, updated_by, version)
SELECT n.notification_id::uuid, 'a0eEBc99-9c0b-4ef8-bb6d-6bb9bd380a11', q.enquiry_id, x.exam_id, a.admit_card_id, 'WHATSAPP',
       'ENTRANCE_EVALUATION_REMINDER', q.guardian_phone,
       'Dear ' || q.guardian_name || ', this is a reminder that ' || a.candidate_name || ' (Application ' || q.application_number
         || ') is scheduled for ' || x.exam_title || ' on ' || to_char(x.exam_start_at AT TIME ZONE 'Asia/Kolkata', 'DD Mon YYYY, HH12:MI AM')
         || ' IST at ' || x.venue || '. Roll No: ' || a.roll_number || ', Seat: ' || a.seat_number
         || '. Please carry the admit card ' || a.admit_card_number || '. ' || x.reporting_instructions,
       n.status, n.provider_id, n.failure, n.dispatched_at, n.dispatched_at, n.dispatched_at, 'meera.counselor@dps.edu.in', NULL, 0
FROM (VALUES
    ('5eed0002-0009-4000-8000-000000000001', '5eed0002-0003-4000-8000-000000000001', 'SENT',   'wamid.HBgMOTE5ODEwMDEyMzAxFQIAERgSN0E1', NULL, TIMESTAMPTZ '2026-09-19 04:30:00+00'),
    ('5eed0002-0009-4000-8000-000000000002', '5eed0002-0003-4000-8000-000000000002', 'SENT',   'wamid.HBgMOTE5ODEwMDEyMzAyFQIAERgSQjI4', NULL, TIMESTAMPTZ '2026-09-19 04:30:05+00'),
    ('5eed0002-0009-4000-8000-000000000003', '5eed0002-0003-4000-8000-000000000003', 'SENT',   'wamid.HBgMOTE5ODEwMDEyMzAzFQIAERgSRjE5', NULL, TIMESTAMPTZ '2026-09-19 04:30:09+00'),
    ('5eed0002-0009-4000-8000-000000000004', '5eed0002-0003-4000-8000-000000000004', 'SENT',   'wamid.HBgMOTE5ODEwMDEyMzA0FQIAERgSMEM3', NULL, TIMESTAMPTZ '2026-09-19 04:30:14+00'),
    ('5eed0002-0009-4000-8000-000000000005', '5eed0002-0003-4000-8000-000000000005', 'SENT',   'wamid.HBgMOTE5ODEwMDEyMzA1FQIAERgSRDY2', NULL, TIMESTAMPTZ '2026-09-19 04:30:18+00'),
    ('5eed0002-0009-4000-8000-000000000006', '5eed0002-0003-4000-8000-000000000006', 'SENT',   'wamid.HBgMOTE5ODEwMDEyMzA2FQIAERgSQTkz', NULL, now() - interval '1 hour'),
    ('5eed0002-0009-4000-8000-000000000007', '5eed0002-0003-4000-8000-000000000007', 'FAILED', NULL,
     'Recipient +919810012307 is not a registered WhatsApp user (provider error 131026)', now() - interval '1 hour')
) AS n(notification_id, admit_card_id, status, provider_id, failure, dispatched_at)
JOIN admit_cards a ON a.admit_card_id = n.admit_card_id::uuid
JOIN entrance_exam_schedules x ON x.exam_id = a.exam_id
JOIN admission_enquiries q ON q.enquiry_id = a.enquiry_id;

-- ---------------------------------------------------------------------------------------------------
-- 10. Audit trail for the seeded actions (created_by = 'seed:module02' marks them for re-runs)
-- ---------------------------------------------------------------------------------------------------
INSERT INTO audit_logs (user_id, ip_address, action, resource, outcome, event_time, details, created_at, updated_at, created_by, updated_by, version)
VALUES
('meera.counselor@dps.edu.in', '10.20.1.15', 'CAPTURE_WALKIN_LEAD', 'admission_enquiry:a0eEBc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'SUCCESS', '2026-08-04 05:10:00+00',
 'Captured walk-in enquiry for Aarav Sharma | Application: APP-2026-3F9A1C01 | Guardian: Rohit Sharma | Phone: +919810012301', '2026-08-04 05:10:00+00', '2026-08-04 05:10:00+00', 'seed:module02', NULL, 0),
('deepak.admissions@dps.edu.in', '10.20.1.22', 'SCHEDULE_ENTRANCE_EXAM', 'entrance_exam:a0eEBc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'SUCCESS', '2026-09-01 06:00:00+00',
 'Scheduled exam EXAM-2026-A1B2C3D4 | Title: Grade 6 Entrance Test 2026-27 (Round 1) | Start: 2026-09-20T03:30:00Z | Venue: Examination Hall, Block A, DPS North Campus | Admit cards generated: 5', '2026-09-01 06:00:00+00', '2026-09-01 06:00:00+00', 'seed:module02', NULL, 0),
('meera.counselor@dps.edu.in', '10.20.1.15', 'DISPATCH_WHATSAPP_EXAM_REMINDERS', 'applicant_notification:a0eEBc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'SUCCESS', '2026-09-19 04:30:20+00',
 'Branch: DPS-DELHI-NORTH | Exams: 1 | Sent: 5 | Skipped: 0 | Failed: 0', '2026-09-19 04:30:20+00', '2026-09-19 04:30:20+00', 'seed:module02', NULL, 0),
('suresh.registrar@dps.edu.in', '10.20.1.31', 'RECORD_APPLICANT_EVALUATION', 'entrance_evaluation:a0eEBc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'SUCCESS', '2026-09-22 06:00:00+00',
 'Recorded INTERVIEW evaluation for application APP-2026-3F9A1C01 | Score: 84.00/100 | Passing: 50.00 | Result: PASSED | Examiner: suresh.registrar@dps.edu.in', '2026-09-22 06:00:00+00', '2026-09-22 06:00:00+00', 'seed:module02', NULL, 0),
('deepak.admissions@dps.edu.in', '10.20.1.22', 'GENERATE_MERIT_LIST', 'merit_list:a0eEBc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'SUCCESS', '2026-09-25 07:00:00+00',
 'Generated MERIT-2026-4D8E21F0 | Grade: GRADE-06 | Year: AY-2026-27 | Weights: entrance 0.700, interview 0.300 | Ranked: 4 | Excluded: 1', '2026-09-25 07:00:00+00', '2026-09-25 07:00:00+00', 'seed:module02', NULL, 0),
('kavya.evaluator@dps.edu.in', '10.20.1.40', 'UPLOAD_APPLICANT_DOCUMENT', 'applicant_document:a0eEBc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'SUCCESS', '2026-09-23 05:40:00+00',
 'Uploaded BIRTH_CERTIFICATE for application APP-2026-3F9A1C01 | Document: 5eed0002-0005-4000-8000-000000000001 | Size: 284311 bytes', '2026-09-23 05:40:00+00', '2026-09-23 05:40:00+00', 'seed:module02', NULL, 0),
('suresh.registrar@dps.edu.in', '10.20.1.31', 'VERIFY_APPLICANT_DOCUMENT', 'applicant_document:a0eEBc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'SUCCESS', '2026-09-26 08:40:00+00',
 'Marked TRANSFER_CERTIFICATE 5eed0002-0005-4000-8000-000000000006 as REJECTED | Application: APP-2026-3F9A1C03', '2026-09-26 08:40:00+00', '2026-09-26 08:40:00+00', 'seed:module02', NULL, 0),
('ramesh.accounts@dps.edu.in', '10.20.1.52', 'ISSUE_ADMISSION_OFFER', 'admission_offer:a0eEBc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'SUCCESS', '2026-09-28 06:00:00+00',
 'Issued offer OFFER-2026-6B3F9A01 for application APP-2026-3F9A1C01 | Grade: GRADE-06 | Fee: INR 25000.00 | Valid until: 2026-10-12', '2026-09-28 06:00:00+00', '2026-09-28 06:00:00+00', 'seed:module02', NULL, 0),
('anita.principal@dps.edu.in', '10.20.1.10', 'COLLECT_SEAT_DEPOSIT', 'admission_fee_deposit:a0eEBc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'SUCCESS', '2026-10-03 11:20:00+00',
 'Collected INR 25000.00 via UPI for offer OFFER-2026-6B3F9A01 | Receipt: RCPT-2026-C2A7E301 | Txn: UPI-424242987612', '2026-10-03 11:20:00+00', '2026-10-03 11:20:00+00', 'seed:module02', NULL, 0),
('rohit.sharma@gmail.com', '49.36.112.87', 'CONVERT_LEAD_TO_STUDENT', 'student_master:a0eEBc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'SUCCESS', '2026-10-05 06:45:00+00',
 'Converted application APP-2026-3F9A1C01 into admission ADM-2026-0F5D8B21 | Student: 5eed0002-0008-4000-8000-000000000001 | Grade: GRADE-06', '2026-10-05 06:45:00+00', '2026-10-05 06:45:00+00', 'seed:module02', NULL, 0),
('ramesh.accounts@dps.edu.in', '10.20.1.52', 'ISSUE_ADMISSION_OFFER', 'admission_offer:a0eEBc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'SUCCESS', '2026-10-10 08:00:00+00',
 'Issued offer OFFER-2026-6B3F9A02 for application APP-2026-3F9A1C02 | Grade: GRADE-06 | Fee: INR 25000.00', '2026-10-10 08:00:00+00', '2026-10-10 08:00:00+00', 'seed:module02', NULL, 0),
('tanya.marketing@dps.edu.in', '10.20.1.60', 'VIEW_LEAD_CONVERSION_REPORT', 'lead_analytics:a0eEBc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'SUCCESS', '2026-10-15 05:00:00+00',
 'Generated conversion report for 2025-10-15 to 2026-10-15 | Leads: 12 | Enrolled: 1 | Rate: 8.33%', '2026-10-15 05:00:00+00', '2026-10-15 05:00:00+00', 'seed:module02', NULL, 0);

-- ---------------------------------------------------------------------------------------------------
-- 11. US-02019 Marketing campaigns and lead attribution
-- ---------------------------------------------------------------------------------------------------
INSERT INTO marketing_campaigns (campaign_id, tenant_id, campaign_code, campaign_name, channel, start_date, end_date, budget_amount,
                                 spend_amount, currency, status, created_at, updated_at, created_by, updated_by, version)
VALUES
('5eed0002-000b-4000-8000-000000000001', 'a0eEBc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'DPS-HOARDINGS-26', 'Hoardings near Rohini and Pitampura metro stations',
 'OUTDOOR', DATE '2026-07-20', DATE '2026-10-31', 75000.00, 75000.00, 'INR', 'ACTIVE', '2026-07-15 05:00:00+00', '2026-10-01 05:00:00+00', 'tanya.marketing@dps.edu.in', 'tanya.marketing@dps.edu.in', 0),
('5eed0002-000b-4000-8000-000000000002', 'a0eEBc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'DPS-GOOGLE-ADS-26', 'Google search ads - "CBSE school North Delhi"',
 'WEBSITE', DATE '2026-07-15', DATE '2026-12-31', 90000.00, 41200.00, 'INR', 'ACTIVE', '2026-07-10 06:00:00+00', '2026-10-15 06:00:00+00', 'tanya.marketing@dps.edu.in', 'tanya.marketing@dps.edu.in', 0),
('5eed0002-000b-4000-8000-000000000003', 'a0eEBc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'DPS-INSTA-OPENHOUSE-26', 'Instagram and WhatsApp click-to-chat ads for the August open house',
 'SOCIAL_MEDIA', DATE '2026-08-01', DATE '2026-11-30', 120000.00, 68500.00, 'INR', 'ACTIVE', '2026-07-25 06:00:00+00', '2026-10-15 06:05:00+00', 'tanya.marketing@dps.edu.in', 'tanya.marketing@dps.edu.in', 0),
('5eed0002-000b-4000-8000-000000000004', 'a0eEBc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'DPS-PARENT-REFERRAL-26', 'Parent referral programme (Rs 2,000 voucher per enrolment)',
 'REFERRAL', DATE '2026-06-01', DATE '2026-09-30', 40000.00, 15000.00, 'INR', 'COMPLETED', '2026-05-25 06:00:00+00', '2026-10-01 04:30:00+00', 'tanya.marketing@dps.edu.in', 'tanya.marketing@dps.edu.in', 0),
('5eed0002-000b-4000-8000-000000000005', 'b1eEBc99-9c0b-4ef8-bb6d-6bb9bd380a22', 'SVKM-ALUMNI-REFERRAL-26', 'Alumni network referrals for Grade 9 lateral entry',
 'REFERRAL', DATE '2026-08-01', DATE '2026-12-31', 30000.00, 6000.00, 'INR', 'ACTIVE', '2026-07-28 05:00:00+00', '2026-10-05 05:00:00+00', 'farah.counselor@svkm.org', 'farah.counselor@svkm.org', 0);

UPDATE admission_enquiries SET campaign_code = c.code
FROM (VALUES ('5eed0002-0001-4000-8000-000000000001', 'DPS-HOARDINGS-26'), ('5eed0002-0001-4000-8000-000000000006', 'DPS-HOARDINGS-26'),
             ('5eed0002-0001-4000-8000-000000000003', 'DPS-GOOGLE-ADS-26'), ('5eed0002-0001-4000-8000-000000000008', 'DPS-GOOGLE-ADS-26'),
             ('5eed0002-0001-4000-8000-000000000012', 'DPS-GOOGLE-ADS-26'),
             ('5eed0002-0001-4000-8000-000000000004', 'DPS-INSTA-OPENHOUSE-26'), ('5eed0002-0001-4000-8000-000000000007', 'DPS-INSTA-OPENHOUSE-26'),
             ('5eed0002-0001-4000-8000-000000000002', 'DPS-PARENT-REFERRAL-26'), ('5eed0002-0001-4000-8000-000000000010', 'DPS-PARENT-REFERRAL-26'),
             ('5eed0002-0001-4000-8000-000000000013', 'SVKM-ALUMNI-REFERRAL-26')) AS c(enquiry_id, code)
WHERE admission_enquiries.enquiry_id = c.enquiry_id::uuid;

-- ---------------------------------------------------------------------------------------------------
-- 12. US-02011 Document follow-ups (what a completeness scan raises for the current pipeline)
-- ---------------------------------------------------------------------------------------------------
INSERT INTO document_follow_ups (follow_up_id, tenant_id, branch_id, enquiry_id, outstanding_documents, issue_summary, follow_up_due_date,
                                 status, flagged_by, resolved_at, resolved_by, resolution_notes, created_at, updated_at, created_by, updated_by, version)
VALUES
('5eed0002-000c-4000-8000-000000000001', 'a0eEBc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'DPS-DELHI-NORTH', '5eed0002-0001-4000-8000-000000000001',
 'TRANSFER_CERTIFICATE', 'TRANSFER_CERTIFICATE: not uploaded', DATE '2026-09-25', 'RESOLVED', 'suresh.registrar@dps.edu.in',
 '2026-09-24 06:20:00+00', 'suresh.registrar@dps.edu.in', 'All mandatory documents verified', '2026-09-22 05:30:00+00', '2026-09-24 06:20:00+00', 'suresh.registrar@dps.edu.in', 'suresh.registrar@dps.edu.in', 0),
('5eed0002-000c-4000-8000-000000000002', 'a0eEBc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'DPS-DELHI-NORTH', '5eed0002-0001-4000-8000-000000000002',
 'TRANSFER_CERTIFICATE', 'TRANSFER_CERTIFICATE: uploaded, awaiting verification', DATE '2026-10-17', 'OPEN', 'suresh.registrar@dps.edu.in',
 NULL, NULL, NULL, '2026-10-14 05:30:00+00', '2026-10-14 05:30:00+00', 'suresh.registrar@dps.edu.in', NULL, 0),
('5eed0002-000c-4000-8000-000000000003', 'a0eEBc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'DPS-DELHI-NORTH', '5eed0002-0001-4000-8000-000000000003',
 'BIRTH_CERTIFICATE,TRANSFER_CERTIFICATE', 'BIRTH_CERTIFICATE: not uploaded; TRANSFER_CERTIFICATE: rejected - Not signed by the previous school principal; please re-upload a signed copy.',
 DATE '2026-09-29', 'OPEN', 'suresh.registrar@dps.edu.in', NULL, NULL, NULL, '2026-09-26 08:45:00+00', '2026-10-14 05:30:00+00', 'suresh.registrar@dps.edu.in', NULL, 0),
('5eed0002-000c-4000-8000-000000000004', 'a0eEBc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'DPS-DELHI-NORTH', '5eed0002-0001-4000-8000-000000000004',
 'BIRTH_CERTIFICATE,TRANSFER_CERTIFICATE', 'BIRTH_CERTIFICATE: not uploaded; TRANSFER_CERTIFICATE: not uploaded', DATE '2026-10-17', 'OPEN', 'suresh.registrar@dps.edu.in',
 NULL, NULL, NULL, '2026-10-14 05:30:00+00', '2026-10-14 05:30:00+00', 'suresh.registrar@dps.edu.in', NULL, 0),
('5eed0002-000c-4000-8000-000000000005', 'a0eEBc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'DPS-DELHI-NORTH', '5eed0002-0001-4000-8000-000000000006',
 'BIRTH_CERTIFICATE,TRANSFER_CERTIFICATE', 'BIRTH_CERTIFICATE: not uploaded; TRANSFER_CERTIFICATE: not uploaded', DATE '2026-10-17', 'OPEN', 'suresh.registrar@dps.edu.in',
 NULL, NULL, NULL, '2026-10-14 05:30:00+00', '2026-10-14 05:30:00+00', 'suresh.registrar@dps.edu.in', NULL, 0),
('5eed0002-000c-4000-8000-000000000006', 'a0eEBc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'DPS-DELHI-NORTH', '5eed0002-0001-4000-8000-000000000007',
 'BIRTH_CERTIFICATE,TRANSFER_CERTIFICATE', 'BIRTH_CERTIFICATE: not uploaded; TRANSFER_CERTIFICATE: not uploaded', DATE '2026-10-17', 'OPEN', 'suresh.registrar@dps.edu.in',
 NULL, NULL, NULL, '2026-10-14 05:30:00+00', '2026-10-14 05:30:00+00', 'suresh.registrar@dps.edu.in', NULL, 0);

-- ---------------------------------------------------------------------------------------------------
-- 13. US-02013 Grade sections and placement
-- ---------------------------------------------------------------------------------------------------
INSERT INTO grade_sections (section_id, tenant_id, branch_id, academic_year_id, grade_id, section_code, capacity, class_teacher_name,
                            created_at, updated_at, created_by, updated_by, version)
VALUES
('5eed0002-000d-4000-8000-000000000001', 'a0eEBc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'DPS-DELHI-NORTH', 'AY-2026-27', 'GRADE-06', 'A', 40, 'Ms. Rekha Joshi',
 '2026-09-15 05:00:00+00', '2026-09-15 05:00:00+00', 'deepak.admissions@dps.edu.in', NULL, 0),
('5eed0002-000d-4000-8000-000000000002', 'a0eEBc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'DPS-DELHI-NORTH', 'AY-2026-27', 'GRADE-06', 'B', 40, 'Mr. Alok Verma',
 '2026-09-15 05:00:00+00', '2026-09-15 05:00:00+00', 'deepak.admissions@dps.edu.in', NULL, 0),
('5eed0002-000d-4000-8000-000000000003', 'a0eEBc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'DPS-DELHI-SOUTH', 'AY-2026-27', 'GRADE-01', 'A', 30, 'Ms. Pooja Arora',
 '2026-09-20 05:00:00+00', '2026-09-20 05:00:00+00', 'deepak.admissions@dps.edu.in', NULL, 0);

UPDATE student_master_records SET section_code = 'A', updated_at = '2026-10-06 05:00:00+00', updated_by = 'deepak.admissions@dps.edu.in'
WHERE student_id = '5eed0002-0008-4000-8000-000000000001';

-- ---------------------------------------------------------------------------------------------------
-- 14. US-02014 Waitlist: 2 lateral seats in Grade 6 (Aarav enrolled + Diya's escalated offer = full)
-- ---------------------------------------------------------------------------------------------------
INSERT INTO waitlist_rules (tenant_id, branch_id, academic_year_id, grade_id, total_seats, offer_validity_days, seat_deposit_amount,
                            auto_escalation_enabled, created_at, updated_at, created_by, updated_by, version)
VALUES ('a0eEBc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'DPS-DELHI-NORTH', 'AY-2026-27', 'GRADE-06', 2, 14, 25000.00, true,
        '2026-09-25 07:30:00+00', '2026-09-25 07:30:00+00', 'seed:module02', 'deepak.admissions@dps.edu.in', 0);

INSERT INTO waitlist_entries (entry_id, tenant_id, branch_id, academic_year_id, grade_id, enquiry_id, priority_score, status, offer_id,
                              offered_at, removal_reason, created_at, updated_at, created_by, updated_by, version)
VALUES
('5eed0002-000e-4000-8000-000000000001', 'a0eEBc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'DPS-DELHI-NORTH', 'AY-2026-27', 'GRADE-06',
 '5eed0002-0001-4000-8000-000000000002', 79.60, 'OFFERED', '5eed0002-0006-4000-8000-000000000002', '2026-10-10 08:00:00+00', NULL,
 '2026-09-26 06:00:00+00', '2026-10-10 08:00:00+00', 'deepak.admissions@dps.edu.in', 'deepak.admissions@dps.edu.in', 0),
('5eed0002-000e-4000-8000-000000000002', 'a0eEBc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'DPS-DELHI-NORTH', 'AY-2026-27', 'GRADE-06',
 '5eed0002-0001-4000-8000-000000000003', 70.80, 'WAITING', NULL, NULL, NULL,
 '2026-09-26 06:00:00+00', '2026-09-26 06:00:00+00', 'deepak.admissions@dps.edu.in', NULL, 0);

-- ---------------------------------------------------------------------------------------------------
-- 15. US-02015 Counseling calls and callbacks
-- ---------------------------------------------------------------------------------------------------
INSERT INTO counseling_calls (call_id, tenant_id, enquiry_id, direction, outcome, duration_seconds, notes, called_at, counselor_id,
                              created_at, updated_at, created_by, updated_by, version)
VALUES
('5eed0002-000f-4000-8000-000000000001', 'a0eEBc99-9c0b-4ef8-bb6d-6bb9bd380a11', '5eed0002-0001-4000-8000-000000000009', 'OUTBOUND', 'CONNECTED', 420,
 'Mother keen on the sports academy (cricket); campus tour booked for Saturday 10 am.', '2026-10-13 05:15:00+00', 'meera.counselor@dps.edu.in',
 '2026-10-13 05:15:00+00', '2026-10-13 05:15:00+00', 'meera.counselor@dps.edu.in', NULL, 0),
('5eed0002-000f-4000-8000-000000000002', 'a0eEBc99-9c0b-4ef8-bb6d-6bb9bd380a11', '5eed0002-0001-4000-8000-000000000008', 'OUTBOUND', 'NO_ANSWER', 0,
 'First follow-up on the fee brochure download; rang out.', now() - interval '2 hours', 'meera.counselor@dps.edu.in',
 now() - interval '2 hours', now() - interval '2 hours', 'meera.counselor@dps.edu.in', NULL, 0),
('5eed0002-000f-4000-8000-000000000003', 'a0eEBc99-9c0b-4ef8-bb6d-6bb9bd380a11', '5eed0002-0001-4000-8000-000000000010', 'INBOUND', 'NOT_INTERESTED', 180,
 'Father called to withdraw: family relocating to Bengaluru in December.', '2026-09-02 09:50:00+00', 'meera.counselor@dps.edu.in',
 '2026-09-02 09:50:00+00', '2026-09-02 09:50:00+00', 'meera.counselor@dps.edu.in', NULL, 0),
('5eed0002-000f-4000-8000-000000000004', 'a0eEBc99-9c0b-4ef8-bb6d-6bb9bd380a11', '5eed0002-0001-4000-8000-000000000012', 'INBOUND', 'CALLBACK_REQUESTED', 95,
 'Mother asked for a call after Diwali about the Grade 1 parent interaction dates.', '2026-10-11 06:35:00+00', 'meera.counselor@dps.edu.in',
 '2026-10-11 06:35:00+00', '2026-10-11 06:35:00+00', 'meera.counselor@dps.edu.in', NULL, 0),
('5eed0002-000f-4000-8000-000000000005', 'b1eEBc99-9c0b-4ef8-bb6d-6bb9bd380a22', '5eed0002-0001-4000-8000-000000000014', 'OUTBOUND', 'CONNECTED', 300,
 'Shared the ICSE syllabus mapping for Grade 9; family visiting the campus on 20 Oct.', '2026-10-06 07:10:00+00', 'farah.counselor@svkm.org',
 '2026-10-06 07:10:00+00', '2026-10-06 07:10:00+00', 'farah.counselor@svkm.org', NULL, 0);

INSERT INTO callback_tasks (callback_id, tenant_id, enquiry_id, source_call_id, scheduled_at, assigned_to, reason, status, closed_at, closing_note,
                            created_at, updated_at, created_by, updated_by, version)
VALUES
('5eed0002-0010-4000-8000-000000000001', 'a0eEBc99-9c0b-4ef8-bb6d-6bb9bd380a11', '5eed0002-0001-4000-8000-000000000008', '5eed0002-000f-4000-8000-000000000002',
 now() + interval '10 minutes', 'meera.counselor@dps.edu.in', 'Retry after no answer (attempt 1 of 3)', 'SCHEDULED', NULL, NULL,
 now() - interval '2 hours', now() - interval '2 hours', 'meera.counselor@dps.edu.in', NULL, 0),
('5eed0002-0010-4000-8000-000000000002', 'a0eEBc99-9c0b-4ef8-bb6d-6bb9bd380a11', '5eed0002-0001-4000-8000-000000000012', '5eed0002-000f-4000-8000-000000000004',
 '2026-10-23 05:30:00+00', 'meera.counselor@dps.edu.in', 'Family requested a callback', 'SCHEDULED', NULL, NULL,
 '2026-10-11 06:35:00+00', '2026-10-11 06:35:00+00', 'meera.counselor@dps.edu.in', NULL, 0);

-- ---------------------------------------------------------------------------------------------------
-- 16. US-02016 Fee waivers (Aarav's merit request was declined; Diya's sibling waiver awaits the principal)
-- ---------------------------------------------------------------------------------------------------
INSERT INTO fee_waivers (waiver_id, tenant_id, offer_id, enquiry_id, scholarship_category, waiver_percent, original_amount, waived_amount,
                         payable_amount, justification, status, requested_by, decided_by, decided_at, decision_notes, waiver_receipt_number,
                         created_at, updated_at, created_by, updated_by, version)
VALUES
('5eed0002-0011-4000-8000-000000000001', 'a0eEBc99-9c0b-4ef8-bb6d-6bb9bd380a11', '5eed0002-0006-4000-8000-000000000001', '5eed0002-0001-4000-8000-000000000001',
 'MERIT', 50, 25000.00, 12500.00, 12500.00, 'Entrance test 88/100 and merit rank 1 for Grade 6.', 'REJECTED', 'deepak.admissions@dps.edu.in',
 'anita.principal@dps.edu.in', '2026-09-30 06:30:00+00', 'Merit scholarship quota for AY 2026-27 is already allotted to Grade 9 entrants.', NULL,
 '2026-09-29 05:00:00+00', '2026-09-30 06:30:00+00', 'deepak.admissions@dps.edu.in', 'anita.principal@dps.edu.in', 0),
('5eed0002-0011-4000-8000-000000000002', 'a0eEBc99-9c0b-4ef8-bb6d-6bb9bd380a11', '5eed0002-0006-4000-8000-000000000002', '5eed0002-0001-4000-8000-000000000002',
 'SIBLING', 25, 25000.00, 6250.00, 18750.00, 'Elder brother is enrolled in Grade 4 at DPS North (referral noted on the enquiry).', 'REQUESTED',
 'deepak.admissions@dps.edu.in', NULL, NULL, NULL, NULL,
 '2026-10-12 06:00:00+00', '2026-10-12 06:00:00+00', 'deepak.admissions@dps.edu.in', NULL, 0);

-- ---------------------------------------------------------------------------------------------------
-- 17. US-02017 Board lookups of transfer certificates
-- ---------------------------------------------------------------------------------------------------
INSERT INTO tc_verifications (verification_id, tenant_id, enquiry_id, document_id, board, tc_number, previous_school_name, result,
                              board_reference, issue_date, response_message, checked_by, checked_at, created_at, updated_at, created_by, updated_by, version)
VALUES
('5eed0002-0012-4000-8000-000000000001', 'a0eEBc99-9c0b-4ef8-bb6d-6bb9bd380a11', '5eed0002-0001-4000-8000-000000000001', '5eed0002-0005-4000-8000-000000000002',
 'CBSE', 'CBSE/2730125/2026/004512', 'St. Mary''s Convent School, Rohini', 'VALID', 'CBSE-REG-2730125-004512', DATE '2026-04-15',
 'Certificate found in the CBSE registry', 'meera.counselor@dps.edu.in', '2026-09-24 06:12:00+00', '2026-09-24 06:12:00+00', '2026-09-24 06:12:00+00', 'meera.counselor@dps.edu.in', NULL, 0),
('5eed0002-0012-4000-8000-000000000002', 'a0eEBc99-9c0b-4ef8-bb6d-6bb9bd380a11', '5eed0002-0001-4000-8000-000000000003', '5eed0002-0005-4000-8000-000000000006',
 'CBSE', 'CBSE/2731840/2026/118000', 'Kendriya Vidyalaya, Pitampura', 'NOT_FOUND', NULL, NULL,
 'No certificate with this number is registered with CBSE', 'meera.counselor@dps.edu.in', '2026-09-25 10:20:00+00', '2026-09-25 10:20:00+00', '2026-09-25 10:20:00+00', 'meera.counselor@dps.edu.in', NULL, 0);

-- ---------------------------------------------------------------------------------------------------
-- 18. US-02018 Acceptance notifications for the two offers
-- ---------------------------------------------------------------------------------------------------
INSERT INTO applicant_notifications (notification_id, tenant_id, enquiry_id, exam_id, admit_card_id, offer_id, channel, notification_type,
                                     recipient_phone, message_body, status, provider_message_id, failure_reason, dispatched_at,
                                     created_at, updated_at, created_by, updated_by, version)
SELECT n.notification_id::uuid, o.tenant_id, q.enquiry_id, NULL, NULL, o.offer_id, 'WHATSAPP', 'ACCEPTANCE_NOTIFICATION', q.guardian_phone,
       'Congratulations ' || q.guardian_name || '! ' || q.candidate_first_name || ' ' || q.candidate_last_name || ' (Application '
         || q.application_number || ') has been offered admission to ' || o.offered_grade_id || ' for ' || o.academic_year_id || '. Offer '
         || o.offer_code || ': please pay INR ' || o.initial_fee_amount || ' by ' || to_char(o.valid_until_date, 'DD Mon YYYY')
         || ' to confirm the seat: ' || o.payment_link_url,
       'SENT', n.provider_id, NULL, n.dispatched_at, n.dispatched_at, n.dispatched_at, 'deepak.admissions@dps.edu.in', NULL, 0
FROM (VALUES
    ('5eed0002-0009-4000-8000-000000000011', '5eed0002-0006-4000-8000-000000000001', 'wamid.HBgMOTE5ODEwMDEyMzAxFQIAERgSQUNDMQ', TIMESTAMPTZ '2026-09-28 06:05:00+00'),
    ('5eed0002-0009-4000-8000-000000000012', '5eed0002-0006-4000-8000-000000000002', 'wamid.HBgMOTE5ODEwMDEyMzAyFQIAERgSQUNDMg', TIMESTAMPTZ '2026-10-10 08:05:00+00')
) AS n(notification_id, offer_id, provider_id, dispatched_at)
JOIN admission_offers o ON o.offer_id = n.offer_id::uuid
JOIN admission_enquiries q ON q.enquiry_id = o.enquiry_id;

-- ---------------------------------------------------------------------------------------------------
-- 19. US-02020 Archive: Advik's withdrawn lead (closed 2 Sep, archived 5 Oct)
-- ---------------------------------------------------------------------------------------------------
INSERT INTO archived_lead_snapshots (archive_batch_id, tenant_id, branch_id, enquiry_id, application_number, final_status, referral_source,
                                     campaign_code, target_grade_id, academic_year_id, enquiry_created_at, closed_at, entrance_test_score,
                                     closure_remarks, archived_at, archived_by, created_at, updated_at, created_by, updated_by, version)
SELECT '5eed0002-0013-4000-8000-000000000001', q.tenant_id, q.branch_id, q.enquiry_id, q.application_number, q.enquiry_status, q.referral_source,
       q.campaign_code, q.target_grade_id, q.academic_year_id, q.created_at, q.updated_at, NULL, q.remarks,
       '2026-10-05 07:00:00+00', 'anita.principal@dps.edu.in', '2026-10-05 07:00:00+00', '2026-10-05 07:00:00+00', 'anita.principal@dps.edu.in', NULL, 0
FROM admission_enquiries q WHERE q.enquiry_id = '5eed0002-0001-4000-8000-000000000010';

UPDATE admission_enquiries SET archived_at = '2026-10-05 07:00:00+00' WHERE enquiry_id = '5eed0002-0001-4000-8000-000000000010';

-- ---------------------------------------------------------------------------------------------------
-- 20. Audit trail for the US-02011 .. US-02020 seed actions
-- ---------------------------------------------------------------------------------------------------
INSERT INTO audit_logs (user_id, ip_address, action, resource, outcome, event_time, details, created_at, updated_at, created_by, updated_by, version)
SELECT a.user_id, a.ip, a.action, a.resource || ':a0eEBc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'SUCCESS', a.at, a.details, a.at, a.at, 'seed:module02', NULL, 0
FROM (VALUES
    ('tanya.marketing@dps.edu.in',   '10.20.1.60', 'CREATE_MARKETING_CAMPAIGN',          'marketing_campaign',     TIMESTAMPTZ '2026-07-15 05:00:00+00', 'Created DPS-HOARDINGS-26 (OUTDOOR) budget INR 75000.00 from 2026-07-20 to 2026-10-31'),
    ('suresh.registrar@dps.edu.in',  '10.20.1.31', 'SCAN_DOCUMENT_FOLLOW_UPS',           'document_follow_up',     TIMESTAMPTZ '2026-10-14 05:30:00+00', 'Branch: DPS-DELHI-NORTH | Scanned: 6 | New flags: 4 | Updated: 1 | Auto-resolved: 0'),
    ('meera.counselor@dps.edu.in',   '10.20.1.15', 'VERIFY_TRANSFER_CERTIFICATE',        'tc_verification',        TIMESTAMPTZ '2026-09-24 06:12:00+00', 'CBSE lookup of TC CBSE/2730125/2026/004512 for APP-2026-3F9A1C01 | Result: VALID | Ref: CBSE-REG-2730125-004512'),
    ('deepak.admissions@dps.edu.in', '10.20.1.22', 'ESCALATE_WAITLIST',                  'waitlist',               TIMESTAMPTZ '2026-10-10 08:00:00+00', 'Grade GRADE-06 (AY-2026-27, DPS-DELHI-NORTH) | Lapsed offers: 0 | Free seats: 1 | Offers issued: 1 | Removed: 0 | Still waiting: 1'),
    ('deepak.admissions@dps.edu.in', '10.20.1.22', 'SEND_BULK_ACCEPTANCE_NOTIFICATIONS', 'applicant_notification', TIMESTAMPTZ '2026-10-10 08:05:00+00', 'Grade GRADE-06 (AY-2026-27, DPS-DELHI-NORTH) | Pool: 1 | Sent: 1 | Skipped: 0 | Failed: 0'),
    ('anita.principal@dps.edu.in',   '10.20.1.10', 'DECIDE_FEE_WAIVER',                  'fee_waiver',             TIMESTAMPTZ '2026-09-30 06:30:00+00', 'REJECTED MERIT waiver 5eed0002-0011-4000-8000-000000000001 on offer OFFER-2026-6B3F9A01 | Payable: INR 12500.00'),
    ('deepak.admissions@dps.edu.in', '10.20.1.22', 'PLACE_STUDENT_IN_SECTION',           'grade_section',          TIMESTAMPTZ '2026-10-06 05:00:00+00', 'Student ADM-2026-0F5D8B21 moved from GRADE-06-unplaced to GRADE-06-A'),
    ('meera.counselor@dps.edu.in',   '10.20.1.15', 'LOG_COUNSELING_CALL',                'counseling_call',        TIMESTAMPTZ '2026-10-13 05:15:00+00', 'OUTBOUND call with APP-2026-3F9A1C09 | Outcome: CONNECTED | Duration: 420s'),
    ('anita.principal@dps.edu.in',   '10.20.1.10', 'ARCHIVE_CLOSED_LEADS',               'lead_archive',           TIMESTAMPTZ '2026-10-05 07:00:00+00', 'Batch 5eed0002-0013-4000-8000-000000000001 | Archived: 1 (rejected 0, withdrawn 1) | Closed less than 30 days ago: 1')
) AS a(user_id, ip, action, resource, at, details);

COMMIT;
