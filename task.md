# Task Execution Record: Module 02 — Admission Management & Lead CRM (US-02002 – US-02100)

Batch 1 (US-02002 – US-02010) is sections 1-6; batch 2 (US-02011 – US-02100) is sections B1-B5 at the end.

Execution of skill [`brd-to-api`](../.agents/skills/brd-to-api/SKILL.md) covering **US-02002 through US-02010** from [`School_ERP_Module_02_Admissions_CRM_1000.md`](../story/module2/School_ERP_Module_02_Admissions_CRM_1000.md).

---

## 1. Input Specification & Target Microservice Resolution

- **Module**: Module 02 — Admission Management, Lead CRM & Student Recruitment Pipeline
- **BRD Source File**: `story/module2/School_ERP_Module_02_Admissions_CRM_1000.md`
- **Target Microservice**: `admissionservice` (existing; US-02001 already implemented here)
- **Base Package**: `com.kvs.admission`
- **Server Port**: `8083`, the value already in `application.yml`. The skill table says 8082; left unchanged.
- **Context path**: `/admissionservice` (`server.servlet.context-path`, override with `SERVER_CONTEXT_PATH`), so the base URL is `http://localhost:8083/admissionservice`.
- **Service Lifecycle Status**: [x] Existing Microservice  [ ] New Microservice
- **Schema reference**: `story/module2/module2.sql` (tables `entrance_evaluations`, `applicant_documents`, `admission_offers`, `admission_fee_deposits` followed and extended)

> Note: the skill's table maps Module 02 to `US-01001`–`US-02000`, but the Module 02 backlog numbers its stories `US-02001`+. They follow US-02001 into `admissionservice`, as the BRD path dictates.

---

## 2. Requirement Extraction

| Story | Actor → Role | Capability | Sub-module header in BRD |
|---|---|---|---|
| US-02002 | Admission Officer → `ROLE_ADMISSION_OFFICER` | Schedule entrance exams, auto-generate admit cards | Admission Inquiry Tracking, Follow-ups & Lead Nurturing CRM |
| US-02003 | Registrar → `ROLE_REGISTRAR` | Evaluate interview scores with standardized rubric | Entrance Examination Scheduling, Roll Numbers & Seating |
| US-02004 | Entrance Exam Evaluator → `ROLE_ENTRANCE_EXAM_EVALUATOR` | Upload birth / transfer certificates to document vault | Candidate Scoring, Assessment Rubrics & Merit List Generation |
| US-02005 | Applicant Parent → `ROLE_APPLICANT_PARENT` | Convert verified leads into Student Master records | Document Vault Upload, Verification & Identity Validation |
| US-02006 | Finance Cashier → `ROLE_FINANCE_CASHIER` | Issue offer letters with embedded fee payment links | Offer Letter Generation, Acceptance & Seat Reservation |
| US-02007 | Marketing Manager → `ROLE_MARKETING_MANAGER` | Track lead conversion rates per channel / referral source | Direct Lead Conversion to Active Student Master Record |
| US-02008 | School Principal → `ROLE_SCHOOL_PRINCIPAL` | Collect seat deposits, issue digital receipts | Admission Fee Collection, Receipting & Deposit Clearance |
| US-02009 | Front-Office Counselor → `ROLE_FRONT_OFFICE_COUNSELOR` | Dispatch automated WhatsApp reminders for entrance evaluations | Referral Attribution, Marketing Campaign ROI & CRM Analytics |
| US-02010 | Admission Officer → `ROLE_ADMISSION_OFFICER` | Rank candidates by weighted entrance test scores | Applicant Communication, Automated SMS/WhatsApp Nudges |

### Acceptance Criteria (identical template for every story)

- [x] **AC-1 (Privileges)**: `@PreAuthorize` per endpoint; story actor + `SUPER_ADMIN` (+ closely related staff roles) allowed.
- [x] **AC-2 (Execution/Input)**: Endpoint executes the capability with valid parameters.
- [x] **AC-3 (Validation)**: Jakarta validation + business-rule checks return `400`.
- [x] **AC-4 (Persistence)**: State persisted transactionally (new tables listed below).
- [x] **AC-5 (Audit Trail)**: `AdmissionAuditService` writes an `audit_logs` row for every story (verified in the end-to-end test).
- [x] **AC-6 (Security/Isolation)**: `401` without token, `403` for wrong role; parents restricted to their own leads (US-02005).

---

## 3. API Contract & Component Mapping

| Story | Method & Path | Controller | Service | Entities (tables) |
|---|---|---|---|---|
| US-02002 | `POST /api/v1/admissions/entrance-exams`, `GET /{examId}`, `GET /{examId}/admit-cards` | `EntranceExamController` | `EntranceExamService` | `EntranceExamSchedule`, `AdmitCard` |
| US-02003 | `GET /api/v1/admissions/evaluation-rubrics`, `POST /evaluations`, `GET /evaluations/{id}`, `GET /enquiries/{id}/evaluations` | `ApplicantEvaluationController` | `ApplicantEvaluationService` | `EntranceEvaluation`, `EvaluationRubricScore` |
| US-02004 | `POST/GET /api/v1/admissions/enquiries/{id}/documents` (multipart), `PATCH /documents/{id}/verification` | `ApplicantDocumentController` | `ApplicantDocumentService`, `DocumentVaultStorage` | `ApplicantDocument` |
| US-02005 | `POST /api/v1/admissions/enquiries/{id}/student-conversion`, `GET /students/{id}` | `StudentConversionController` | `StudentConversionService` | `StudentMasterRecord` |
| US-02006 | `POST /api/v1/admissions/offers`, `GET /offers/{id}`, `GET /enquiries/{id}/offers` | `AdmissionOfferController` | `AdmissionOfferService` | `AdmissionOffer` |
| US-02007 | `GET /api/v1/admissions/analytics/conversion-rates` | `AdmissionAnalyticsController` | `LeadConversionAnalyticsService` | (reads `AdmissionEnquiry`) |
| US-02008 | `POST /api/v1/admissions/offers/{id}/deposits`, `GET /deposits/receipts/{receiptNumber}` | `AdmissionFeeDepositController` | `AdmissionFeeDepositService` | `AdmissionFeeDeposit` |
| US-02009 | `POST /api/v1/admissions/reminders/entrance-evaluations` (+ optional scheduler) | `ApplicantReminderController` | `ApplicantReminderService`, `ApplicantReminderScheduler`, `WhatsAppGateway` | `ApplicantNotification` |
| US-02010 | `POST /api/v1/admissions/merit-lists`, `GET /merit-lists/{id}` | `MeritListController` | `MeritListService`, `MeritRankingCalculator` | `MeritList`, `MeritListEntry` |

Shared additions: `enums/*` (status and type enums), `AdmissionAuditService` (audit writer), `ReferenceNumberGenerator`, and `exception/AdmissionRequestExceptionHandler`. The handler maps malformed JSON, unknown enum values, missing parameters or parts, and oversized uploads to `400`/`413`; the core `GlobalExceptionHandler` would otherwise answer `500`.

Pipeline status flow driven by these stories:
`NEW_INQUIRY → EVALUATION_SCHEDULED (02002) → EVALUATION_PASSED (02003) → OFFER_ISSUED (02006) → [offer ACCEPTED by deposit 02008] → ENROLLED (02005)`

---

## 4. Implementation Checklist

- [x] Step 0: Scaffolding — not needed (service exists)
- [x] Step 1: Entities extending `BaseAuditEntity`, with unique constraints and indexes (9 new tables)
- [x] Step 2: Request DTOs with Jakarta validation; response DTOs with Lombok `@Builder`
- [x] Step 3: Repositories with finders, aggregate JPQL (US-02007) and a pessimistic lock (US-02008)
- [x] Step 4: Services — `@Transactional`, domain exceptions, audit entries
- [x] Step 5: Controllers — `@RestController`, `@PreAuthorize`, `@Valid`, `ApiResponse<T>` envelope
- [x] Step 6: Tests — 8 controller test classes (201/400/401/403/404/409, trace headers), 1 end-to-end pipeline test with the real DB, 3 unit test classes
- [x] Step 7: Single service collection `postman/School_ERP_AdmissionService.postman_collection.json` with one folder per story, US-02001 … US-02010 (v2.1, positive + negative, `pm.test` assertions, ID capture chaining the folders)
- [x] Step 8: Single consolidated Confluence documentation file [`wiki/api-docs/School_ERP_Module_02_Admissions_API_Documentation.md`](wiki/api-docs/School_ERP_Module_02_Admissions_API_Documentation.md) with native Confluence `{expand:title=...}` blocks covering US-02001 through US-02020 and multi-scenario actor access matrix.

New configuration (`application.yml`): `admission.document-vault.*`, `admission.payment.link-base-url`, `admission.reminders.auto-dispatch.*`, multipart limit 5 MB.

---

## 5. Verification

```bash
cd admissionservice
.\gradlew.bat test
```

Result: **86 tests, 0 failures, 0 errors, 0 skipped** (includes the existing 11 US-02001 tests).

| Test class | Tests |
|---|---|
| AdmissionPipelineEndToEndTest (US-02001…02010 through HTTP + PostgreSQL) | 1 |
| EntranceExamControllerIntegrationTest | 8 |
| ApplicantEvaluationControllerIntegrationTest | 8 |
| ApplicantDocumentControllerIntegrationTest | 8 |
| StudentConversionControllerIntegrationTest | 8 |
| AdmissionOfferControllerIntegrationTest | 7 |
| AdmissionAnalyticsControllerIntegrationTest | 6 |
| AdmissionFeeDepositControllerIntegrationTest | 7 |
| ApplicantReminderControllerIntegrationTest | 5 |
| MeritListControllerIntegrationTest | 6 |
| MeritRankingCalculatorTest / LeadConversionAnalyticsServiceTest / DocumentVaultStorageTest | 5 / 2 / 4 |
| AdmissionEnquiryControllerIntegrationTest (existing US-02001) | 11 |

The Postman collection was validated as JSON (every `{{variable}}` is defined) but **not executed** against a running service.

## 6. Known Gaps / Follow-ups

- **WhatsApp**: `LoggingWhatsAppGateway` is a stub. A real provider needs a `WhatsAppGateway` bean.
- **Offer letters**: content and payment link are generated and stored, but not emailed or SMSed. The payment page behind the link is out of scope (Module 04 finance).
- **Student Master**: `student_master_records` is the admission-side hand-off. The Module 03 `studentservice` does not exist yet to consume it.
- **Schema**: tables are created by Hibernate (now `ddl-auto: update`). `module2.sql` should be updated with the new tables before moving to Flyway.

---

# Batch 2: US-02011 – US-02100

Same skill, same service. The 90 stories are 10 new capabilities (US-02011 – US-02020, each repeated as Scenarios 1-5) plus Scenarios 2-5 of the 10 capabilities from batch 1. The backlog rotates actors, so each story's actor was **granted where it fits the business process and not granted otherwise** (decision confirmed by the product owner in this session). The full table is [`wiki/api-docs/US-02011-US-02100-scenario-actor-matrix.md`](wiki/api-docs/US-02011-US-02100-scenario-actor-matrix.md): **56 granted, 34 not granted**, each asserted by `ScenarioActorAccessTest`.

## B1. New capabilities

| Story (scenarios) | Capability | Controller / Service | Tables | Granted backlog actors |
|---|---|---|---|---|
| US-02011 (/31/51/71/91) | Flag incomplete document submissions, auto-resolve when verified | `DocumentFollowUpController` / `DocumentFollowUpService` | `document_follow_ups` | Registrar |
| US-02012 (/32/52/72/92) | Merit list noticeboard PDF (masked names by default) | `MeritListController` / `MeritListPdfRenderer` (PDFBox 3.0.3) | — | Evaluator, Principal |
| US-02013 (/33/53/73/93) | Grade sections, auto-placement, manual placement | `SectionPlacementController` / `SectionPlacementService` | `grade_sections`, `student_master_records.section_code` | none (Admission Officer / Registrar do it; parents see it on the student record) |
| US-02014 (/34/54/74/94) | Waitlist queue, seat rule, escalation (+ optional daily scheduler) | `WaitlistController` / `WaitlistService` | `waitlist_rules`, `waitlist_entries` | Admission Officer |
| US-02015 (/35/55/75/95) | Call notes with automatic callbacks | `CounselingCallController` / `CounselingCallService` | `counseling_calls`, `callback_tasks` | Marketing Manager (read-only) |
| US-02016 (/36/56/76/96) | Scholarship fee waivers with category caps and four-eyes approval | `FeeWaiverController` / `FeeWaiverService` | `fee_waivers` | Principal |
| US-02017 (/37/57/77/97) | Transfer certificate check with the education board (stub gateway) | `TransferCertificateVerificationController` / `…Service` | `tc_verifications` | Counselor |
| US-02018 (/38/58/78/98) | Bulk acceptance notifications (WhatsApp, idempotent per offer) | `AcceptanceNotificationController` / `AcceptanceNotificationService` | `applicant_notifications.offer_id` | Admission Officer |
| US-02019 (/39/59/79/99) | Marketing campaigns and cost-per-acquisition report | `MarketingCampaignController` / `MarketingCampaignService` | `marketing_campaigns`, `admission_enquiries.campaign_code` | Marketing Manager |
| US-02020 (/40/60/80/100) | Archive closed leads with trend snapshots | `LeadArchiveController` / `LeadArchiveService` | `archived_lead_snapshots`, `admission_enquiries.archived_at` | Principal |

## B2. Scenario repeats of batch-1 capabilities (40 stories)

Covered by the existing endpoints; roles added where the actor fits:

| Capability | Granted (role added) | Not granted |
|---|---|---|
| Capture enquiry (US-02021/41/61/81) | Applicant Parent - own enquiry only, guardian email must equal login email (added); Counselor | — |
| Schedule exam (US-02022/42/62/82) | Admission Officer | Finance Cashier |
| Evaluate (US-02023/43/63/83) | Registrar | Marketing Manager |
| Upload documents (US-02024/44/64/84) | School Principal (added), Evaluator | — |
| Convert lead (US-02025/45/65/85) | Front-Office Counselor (added), Parent | — |
| Issue offer (US-02026/46/66/86) | Admission Officer, Cashier | — |
| Conversion analytics (US-02027/47/67/87) | Registrar read-only (added), Marketing Manager | — |
| Collect deposit (US-02028/48/68/88) | Principal | Entrance Exam Evaluator |
| Reminders (US-02029/49/69/89) | Counselor | Applicant Parent |
| Rank candidates (US-02030/50/70/90) | Admission Officer | Finance Cashier |

Shared changes: `AdmissionAccessGuard` (staff-or-own-guardian rule used by enquiries, conversion and student records), `PaymentMode.WAIVER`, `NotificationType.ACCEPTANCE_NOTIFICATION`; the deposit endpoint now expects the waiver-adjusted amount and rejects manual `WAIVER` payments.

## B3. Database

- New tables and columns are added by Hibernate (`ddl-auto: update`).
- **Existing databases must run** [`db/upgrade/module02_us02011_us02020_enum_constraints.sql`](src/main/resources/db/upgrade/module02_us02011_us02020_enum_constraints.sql) once: Hibernate's enum CHECK constraints are not updated by `ddl-auto: update`, so `WAIVER` and `ACCEPTANCE_NOTIFICATION` are otherwise rejected. Applied to the local `authservice` database.
- Seed [`db/seed/module02_admission_seed.sql`](src/main/resources/db/seed/module02_admission_seed.sql) extended for every new table (campaigns, follow-ups, sections, waitlist, calls, callbacks, waivers, TC lookups, acceptance notices, archive) and executed; re-run safe.

## B4. Verification

`.\gradlew.bat test`: **231 tests, 0 failures, 0 errors** (35 test classes), including:

| Test | What it proves |
|---|---|
| `AdmissionOperationsEndToEndTest` | US-02011 – US-02020 and the parent/counselor grants through HTTP + PostgreSQL in one cohort |
| `ScenarioActorAccessTest` (90 cases) | every story's actor decision: granted → 2xx, not granted → 403 |
| 9 new controller test classes + `MeritListPdfExportIntegrationTest` | validation, 401/403, domain errors per endpoint |
| `AdmissionRulesUnitTest` | document check, PDF masking/pagination, section balancing, callback rules, waiver caps, board stub, CPA, trend buckets |

Postman: 100 story folders / 218 requests in `School_ERP_AdmissionService.postman_collection.json` (JSON and variables validated, **not executed** against a running service). Scenario-repeat folders contain one authorization probe: granted actors must not get 401/403; others must get 403.

## B5. Known gaps

- **Board registries**: `StubEducationBoardGateway` answers deterministically; no CBSE/ICSE/IB/state API is integrated.
- **Notifications**: acceptance notices use the WhatsApp logging stub; no email channel.
- **Schedulers**: waitlist escalation and reminders schedulers are off by default (`WAITLIST_AUTO_ESCALATION_ENABLED`, `REMINDER_AUTO_DISPATCH_ENABLED`).
- **US-02013 / US-02029 / some repeats**: where no backlog actor was granted, the capability is still delivered through the roles that own the process; confirm the matrix with the business before go-live.
