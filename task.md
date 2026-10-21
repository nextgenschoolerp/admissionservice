# Task Execution Record: Module 02 — Admission Management, Lead CRM & Student Recruitment Pipeline

Execution of the `BRD_API_Generate` skill for **Module 02** based on [`story/module2/School_ERP_Module_02_Admissions_CRM_1000.md`](../story/module2/School_ERP_Module_02_Admissions_CRM_1000.md) and [`module2.sql`](../story/module2/module2.sql).

---

## 1. Input Specification & Target Microservice Resolution

- **User Stories**: `US-02001` … `US-02020` (the 20 base stories, scenario 1 of each) and `US-02021` … `US-02100` (80 scenario variations). `ScenarioActorAccessTest` checks the actor decision of every story from US-02011 to US-02100 (90 cases). The BRD's 1,000 entries are 50 variations of the 20 base stories.
- **Module**: `02` — Admission Management, Lead CRM & Student Recruitment Pipeline
- **Target Microservice**: `admissionservice`
- **Base Package**: `com.kvs.admission`
- **Allocated Server Port**: `8083`, context path `/admissionservice` ([`application.yml`](src/main/resources/application.yml), `SERVER_PORT` / `SERVER_CONTEXT_PATH`). Test port `18083`.
- **Database**: shared PostgreSQL database `authservice` (as authservice, studentservice, financeservice and examservice), tables created by Hibernate `ddl-auto: update`; existing databases also need the one-off enum upgrade script (§4, Step 9)
- **Service Lifecycle Status**:
  - [x] Existing Microservice (US-02001 was built first; US-02002 … US-02020 added in two batches)
  - [ ] New Microservice
- **Reference Architecture**: `references/java-patterns.md`; Spring Boot 3.2.5, Java 21, `com.kvs.core:ms-core:1.0.0-SNAPSHOT`, PDFBox 3.0.3 (merit list PDF)
- **Downstream**: *Enrol student* hands the candidate to studentservice (`POST /api/v1/students`); finance fees start from the class fee schedule in financeservice.

---

## 2. Requirement Extraction (from BRD)

| Story | Capability | Actor → Role |
|---|---|---|
| US-02001 | Capture walk-in and online enquiries (leads) | Front-Office Counselor → `ROLE_FRONT_OFFICE_COUNSELOR`; Admission Officer; Applicant Parent (own enquiry) |
| US-02002 | Schedule entrance exams and issue admit cards | Admission Officer → `ROLE_ADMISSION_OFFICER` |
| US-02003 | Score entrance tests and interviews with a standard rubric | Registrar → `ROLE_REGISTRAR` |
| US-02004 | Upload birth / transfer certificates to the document vault and verify them | Entrance Exam Evaluator → `ROLE_ENTRANCE_EXAM_EVALUATOR`; Registrar |
| US-02005 | Convert a verified lead into a Student Master record (enrol) | Applicant Parent → `ROLE_APPLICANT_PARENT`; Registrar |
| US-02006 | Issue offer letters with an embedded fee payment link | Finance Cashier → `ROLE_FINANCE_CASHIER`; Admission Officer |
| US-02007 | Lead conversion rates per channel / referral source | Marketing Manager → `ROLE_MARKETING_MANAGER` |
| US-02008 | Collect seat deposits and issue digital receipts | School Principal → `ROLE_SCHOOL_PRINCIPAL`; Finance Cashier |
| US-02009 | WhatsApp reminders for entrance evaluations | Front-Office Counselor |
| US-02010 | Rank candidates by weighted entrance scores (merit list) | Admission Officer |
| US-02011 | Flag incomplete document submissions; auto-resolve when verified | Registrar |
| US-02012 | Merit list noticeboard PDF (masked names by default) | Entrance Exam Evaluator; Principal |
| US-02013 | Grade sections, auto-placement and manual placement | Admission Officer; Registrar |
| US-02014 | Waitlist queue with a seat rule and escalation | Admission Officer |
| US-02015 | Counselling call notes with automatic callbacks | Front-Office Counselor; Marketing Manager (read) |
| US-02016 | Scholarship fee waivers with category caps and four-eyes approval | Admission Officer / Registrar request; Principal decides |
| US-02017 | Transfer certificate check with the education board | Front-Office Counselor; Registrar |
| US-02018 | Bulk acceptance notifications (idempotent per offer) | Admission Officer |
| US-02019 | Marketing campaigns and cost per acquisition | Marketing Manager |
| US-02020 | Archive closed leads with trend snapshots | Principal; Admission Officer |

The BRD rotates actors across the scenario variations; each US-02011 … US-02100 actor was **granted where it fits the business process and refused otherwise** (decision confirmed by the product owner). The full table is [`wiki/api-docs/US-02011-US-02100-scenario-actor-matrix.md`](wiki/api-docs/US-02011-US-02100-scenario-actor-matrix.md); each decision is asserted by `ScenarioActorAccessTest`.

Pipeline status flow: `NEW_INQUIRY → CONTACTED (02015) → EVALUATION_SCHEDULED (02002) → EVALUATION_PASSED (02003) → OFFER_ISSUED (02006) → [offer ACCEPTED by deposit 02008] → ENROLLED (02005)`; any open application can be closed as `REJECTED` (Not offered) or `WITHDRAWN`.

### Acceptance Criteria (each story)
- [x] **AC-1 (Privileges)**: `@PreAuthorize` per endpoint (§3), served live by `GET /api/v1/admissions/access-rules`; parents only act on enquiries registered under their own e-mail (`AdmissionAccessGuard`)
- [x] **AC-2 (Execution/Input)**: endpoints accept the documented payloads
- [x] **AC-3 (Validation)**: Jakarta validation and business rules → `400`; malformed JSON, unknown enum values, missing parts → `400`, oversized uploads → `413` (`AdmissionRequestExceptionHandler`)
- [x] **AC-4 (Persistence)**: transactional services; the deposit takes a pessimistic lock on the offer
- [x] **AC-5 (Audit Trail)**: `AdmissionAuditService` writes an `audit_logs` row for every story (asserted in the end-to-end tests)
- [x] **AC-6 (Security/Isolation)**: `401` without a token, `403` for other roles, `409` for duplicate or closed records

---

## 3. API Contract & Architectural Design

Paths are under `/api/v1/admissions`. Roles are the `@PreAuthorize` of each endpoint, read from the running service (`GET /api/v1/admissions/access-rules`, 56 endpoints); `SUPER_ADMIN` is allowed everywhere.

| Method | Path | Story | Roles |
|---|---|---|---|
| `POST` | `/enquiries` | US-02001 | FRONT_OFFICE_COUNSELOR, ADMISSION_OFFICER, APPLICANT_PARENT (own) |
| `GET` | `/enquiries`, `/enquiries/{enquiryId}` | US-02001 | FRONT_OFFICE_COUNSELOR, ADMISSION_OFFICER, REGISTRAR, ENTRANCE_EXAM_EVALUATOR, FINANCE_CASHIER, SCHOOL_PRINCIPAL (+ APPLICANT_PARENT for one enquiry) |
| `PATCH` | `/enquiries/{enquiryId}/decision` | – (Not offered / Withdrawn) | ADMISSION_OFFICER, SCHOOL_PRINCIPAL |
| `POST` | `/entrance-exams` | US-02002 | ADMISSION_OFFICER |
| `GET` | `/entrance-exams/{examId}`, `/{examId}/admit-cards` | US-02002 | ADMISSION_OFFICER, FRONT_OFFICE_COUNSELOR |
| `GET` | `/evaluation-rubrics` | US-02003 | REGISTRAR, ENTRANCE_EXAM_EVALUATOR, ADMISSION_OFFICER |
| `POST` / `GET` | `/evaluations`, `/evaluations/{evaluationId}`, `/enquiries/{enquiryId}/evaluations` | US-02003 | REGISTRAR, ADMISSION_OFFICER |
| `POST` | `/enquiries/{enquiryId}/documents` (multipart) | US-02004 | ENTRANCE_EXAM_EVALUATOR, SCHOOL_PRINCIPAL, ADMISSION_OFFICER |
| `GET` | `/enquiries/{enquiryId}/documents` | US-02004 | ENTRANCE_EXAM_EVALUATOR, REGISTRAR, ADMISSION_OFFICER |
| `PATCH` | `/documents/{documentId}/verification` | US-02004 | REGISTRAR, ADMISSION_OFFICER |
| `POST` | `/enquiries/{enquiryId}/student-conversion` | US-02005 | APPLICANT_PARENT (own), FRONT_OFFICE_COUNSELOR, ADMISSION_OFFICER, REGISTRAR |
| `GET` | `/students/{studentId}` | US-02005 | APPLICANT_PARENT (own), FRONT_OFFICE_COUNSELOR, ADMISSION_OFFICER, REGISTRAR |
| `POST` | `/offers` | US-02006 | FINANCE_CASHIER, ADMISSION_OFFICER |
| `GET` | `/offers/{offerId}`, `/enquiries/{enquiryId}/offers` | US-02006 | FINANCE_CASHIER, ADMISSION_OFFICER, SCHOOL_PRINCIPAL |
| `GET` | `/analytics/conversion-rates` | US-02007 | MARKETING_MANAGER, REGISTRAR, ADMISSION_OFFICER |
| `POST` | `/offers/{offerId}/deposits` | US-02008 | SCHOOL_PRINCIPAL, FINANCE_CASHIER |
| `GET` | `/deposits/receipts/{receiptNumber}` | US-02008 | SCHOOL_PRINCIPAL, FINANCE_CASHIER, ADMISSION_OFFICER |
| `POST` | `/reminders/entrance-evaluations` | US-02009 | FRONT_OFFICE_COUNSELOR, ADMISSION_OFFICER |
| `POST` | `/merit-lists` | US-02010 | ADMISSION_OFFICER |
| `GET` | `/merit-lists/{meritListId}` | US-02010 | ADMISSION_OFFICER, REGISTRAR, SCHOOL_PRINCIPAL |
| `GET` / `POST` / `PATCH` | `/document-follow-ups`, `/document-follow-ups/scan`, `/{followUpId}/resolution` | US-02011 | list: REGISTRAR, ADMISSION_OFFICER, FRONT_OFFICE_COUNSELOR; scan / resolve: REGISTRAR, ADMISSION_OFFICER |
| `GET` | `/merit-lists/{meritListId}/pdf` | US-02012 | ENTRANCE_EXAM_EVALUATOR, SCHOOL_PRINCIPAL, ADMISSION_OFFICER, REGISTRAR |
| `GET` / `PUT` / `POST` | `/grade-sections`, `/grade-sections/auto-placement`, `PUT /students/{studentId}/placement` | US-02013 | list: ADMISSION_OFFICER, REGISTRAR, FRONT_OFFICE_COUNSELOR, SCHOOL_PRINCIPAL; change: ADMISSION_OFFICER, REGISTRAR |
| `GET` | `/waitlists` | US-02014 | ADMISSION_OFFICER, REGISTRAR, SCHOOL_PRINCIPAL |
| `PUT` / `POST` / `DELETE` | `/waitlists/rules`, `/waitlists/entries[/{entryId}]`, `/waitlists/escalation` | US-02014 | ADMISSION_OFFICER |
| `POST` / `GET` | `/enquiries/{enquiryId}/calls` | US-02015 | log: FRONT_OFFICE_COUNSELOR, ADMISSION_OFFICER; list: + MARKETING_MANAGER |
| `GET` / `PATCH` | `/callbacks`, `/callbacks/{callbackId}/cancellation` | US-02015 | list: FRONT_OFFICE_COUNSELOR, ADMISSION_OFFICER, MARKETING_MANAGER; cancel: FRONT_OFFICE_COUNSELOR, ADMISSION_OFFICER |
| `POST` | `/offers/{offerId}/fee-waivers` | US-02016 | ADMISSION_OFFICER, REGISTRAR, SCHOOL_PRINCIPAL |
| `GET` | `/fee-waivers[/{waiverId}]` | US-02016 | ADMISSION_OFFICER, REGISTRAR, SCHOOL_PRINCIPAL, FINANCE_CASHIER |
| `PATCH` | `/fee-waivers/{waiverId}/decision` | US-02016 | SCHOOL_PRINCIPAL |
| `POST` / `GET` | `/enquiries/{enquiryId}/transfer-certificate/verifications` | US-02017 | FRONT_OFFICE_COUNSELOR, REGISTRAR, ADMISSION_OFFICER |
| `POST` | `/acceptance-notifications` | US-02018 | ADMISSION_OFFICER |
| `GET` / `POST` / `PATCH` | `/marketing-campaigns[/{campaignId}]` | US-02019 | list: MARKETING_MANAGER, ADMISSION_OFFICER, FRONT_OFFICE_COUNSELOR; change: MARKETING_MANAGER |
| `GET` | `/analytics/cost-per-acquisition` | US-02019 | MARKETING_MANAGER, ADMISSION_OFFICER |
| `POST` | `/lead-archives` | US-02020 | SCHOOL_PRINCIPAL, ADMISSION_OFFICER |
| `GET` | `/analytics/archived-lead-trends` | US-02020 | SCHOOL_PRINCIPAL, ADMISSION_OFFICER, MARKETING_MANAGER |
| `GET` | `/access-rules` | – (tooling) | any signed-in user |

### Component Mapping
- **Entities** (extend `BaseAuditEntity`): `AdmissionEnquiry`, `EntranceExamSchedule`, `AdmitCard`, `EntranceEvaluation`, `EvaluationRubricScore`, `ApplicantDocument`, `StudentMasterRecord`, `AdmissionOffer`, `AdmissionFeeDeposit`, `ApplicantNotification`, `MeritList`, `MeritListEntry`, `DocumentFollowUp`, `GradeSection`, `WaitlistRule`, `WaitlistEntry`, `CounselingCall`, `CallbackTask`, `FeeWaiver`, `TcVerification`, `MarketingCampaign`, `ArchivedLeadSnapshot`, `AuditLog`
- **Services**: one per capability (`AdmissionEnquiryService`, `EntranceExamService`, `ApplicantEvaluationService`, `ApplicantDocumentService` + `DocumentVaultStorage`, `StudentConversionService`, `AdmissionOfferService`, `LeadConversionAnalyticsService`, `AdmissionFeeDepositService`, `ApplicantReminderService` + scheduler + `WhatsAppGateway`, `MeritListService` + `MeritRankingCalculator` + `MeritListPdfRenderer`, `DocumentFollowUpService`, `SectionPlacementService`, `WaitlistService`, `CounselingCallService`, `FeeWaiverService`, `TransferCertificateVerificationService`, `AcceptanceNotificationService`, `MarketingCampaignService`, `LeadArchiveService`); shared `AdmissionAccessGuard`, `AdmissionAuditService`, `ReferenceNumberGenerator`
- **Controllers** (20): `AdmissionEnquiryController`, `EntranceExamController`, `ApplicantEvaluationController`, `ApplicantDocumentController`, `StudentConversionController`, `AdmissionOfferController`, `AdmissionAnalyticsController`, `AdmissionFeeDepositController`, `ApplicantReminderController`, `MeritListController`, `DocumentFollowUpController`, `SectionPlacementController`, `WaitlistController`, `CounselingCallController`, `FeeWaiverController`, `TransferCertificateVerificationController`, `AcceptanceNotificationController`, `MarketingCampaignController`, `LeadArchiveController`, `AccessRuleController`

### Design notes
- **One enquiry per guardian phone and e-mail per tenant**: a second application with the same contact answers `409` naming the existing application.
- **Application details reused at enrolment** (19 Oct 2026): an enquiry can record the candidate's date of birth, gender and the guardian relationship (`candidateDateOfBirth`, `candidateGender`, `guardianRelationship`, all optional). The conversion body may be empty; it falls back to those and answers `400` only when the application has none. The UI's *Enrol student* step then creates the studentservice record in the chosen section with the same details.
- **Closing an application** (`PATCH /enquiries/{id}/decision`): `REJECTED` (Not offered) or `WITHDRAWN` with a required reason appended to the remarks; `409` when already closed. No API set `REJECTED` before.
- **Read access for stakeholders**: `GET /enquiries` and `/enquiries/{id}` also allow REGISTRAR, ENTRANCE_EXAM_EVALUATOR, FINANCE_CASHIER and SCHOOL_PRINCIPAL so each can open the applications they act on.
- **Fee waivers**: the deposit endpoint expects the waiver-adjusted amount and rejects manual `WAIVER` payments; a fully waived deposit uses `PaymentMode.WAIVER`.
- **Stubs**: WhatsApp (`LoggingWhatsAppGateway`) and the education board (`StubEducationBoardGateway`) answer deterministically; serials ending in `000` are "not found".

---

## 4. Implementation Checklist

### Step 0: Microservice Scaffolding
- [x] Not needed: the service existed for US-02001 (port 8083, context path, shared DB, JWT, tracing)
- [x] Configuration added: `admission.document-vault.*`, `admission.payment.link-base-url`, `admission.reminders.auto-dispatch.*`, waitlist auto-escalation, multipart limit 5 MB

### Steps 1–6
- [x] 23 entities with unique constraints and indexes
- [x] Request DTOs with Jakarta validation; response DTOs with Lombok builders
- [x] Repositories with finders, aggregate JPQL (US-02007) and a pessimistic lock (US-02008)
- [x] Services with domain exceptions and audit entries
- [x] Controllers with `@PreAuthorize`, `@Valid`, `ApiResponse<T>`
- [x] Tests: controller tests per story (201/400/401/403/404/409, trace headers), two end-to-end pipelines over HTTP + PostgreSQL, the scenario actor matrix and unit tests — **240 tests, 0 failures**
- [x] `GET /api/v1/admissions/access-rules` (endpoint → roles, read from the running service) for the workflow runners

### Step 7: Postman
- [x] [`postman/School_ERP_AdmissionService.postman_collection.json`](postman/School_ERP_AdmissionService.postman_collection.json): 100 story folders, 218 requests (positive and negative, `pm.test` assertions, ids chained); scenario-repeat folders hold one authorization probe each

### UI mapping
- [x] Vite proxy `/api/v1/admissions` → `http://localhost:8083/admissionservice` (`ADMISSION_SERVICE_URL`)
- [x] Students → Admissions tab, Review application with **Next step** per stakeholder (log a call, schedule exam, record assessment, issue offer, collect deposit, enrol, not offered / withdrawn), document verify / reject, Add student (application with documents) — [`frontend/docs/admissionservice-api-mapping.md`](../frontend/docs/admissionservice-api-mapping.md)
- [x] Workflow runners: [`/dev/admission-workflow.html`](../frontend/dev/admission-workflow.html) (enquiry → enrolled, 14 steps) and [`/dev/student-lifecycle-workflow.html`](../frontend/dev/student-lifecycle-workflow.html) (enquiry → promotion, 15 steps)

### Step 8: API Wiki
- [x] [`wiki/api-docs/School_ERP_Module_02_Admissions_API_Documentation.md`](wiki/api-docs/School_ERP_Module_02_Admissions_API_Documentation.md) with `{expand}` blocks per story (US-02001 … US-02020) and the scenario actor matrix
- [x] [`wiki/api-docs/US-02011-US-02100-scenario-actor-matrix.md`](wiki/api-docs/US-02011-US-02100-scenario-actor-matrix.md): 56 granted, 34 not granted
- [x] [`wiki/workflows/DPS_Enquiry_to_Admission_Workflow.md`](wiki/workflows/DPS_Enquiry_to_Admission_Workflow.md)

### Step 9: Seed and database upgrade
- [x] [`src/main/resources/db/seed/module02_admission_seed.sql`](src/main/resources/db/seed/module02_admission_seed.sql) (re-runnable, ids `5eed0002-…`): DPS North Grade 6 pipeline for AY 2026-27 — 14 enquiries in every status, 2 exams with 7 admit cards, 9 evaluations, 6 vault documents, Aarav Sharma enrolled, 2 offers, a deposit, reminders, a merit list, follow-ups, sections, a waitlist, calls and callbacks, waivers, TC lookups, acceptance notices, 5 campaigns and an archived lead
- [x] [`src/main/resources/db/upgrade/module02_us02011_us02020_enum_constraints.sql`](src/main/resources/db/upgrade/module02_us02011_us02020_enum_constraints.sql): run once on databases created before US-02011 (Hibernate does not update enum CHECK constraints)

---

## 5. Verification & Quality Gates

```bash
cd admissionservice
.\gradlew.bat test
```

Run on 19 Oct 2026:

| Test class | Tests | What it proves |
|---|---|---|
| `AdmissionPipelineEndToEndTest` | 1 | US-02001 … US-02010 over HTTP + PostgreSQL in one cohort, incl. enrolment from the application's date of birth |
| `AdmissionOperationsEndToEndTest` | 1 | US-02011 … US-02020 and the parent / counselor grants over HTTP + PostgreSQL |
| `ScenarioActorAccessTest` (US-02011 … US-02100) | 90 | every actor decision: granted → 2xx, not granted → 403 |
| `AdmissionUsernameTokenAccessTest` | 3 | authservice-shaped tokens (username subject, e-mail claim), counselor ownership, parent self-service |
| `AdmissionEnquiryControllerIntegrationTest` | 15 | capture, list, get, close (Not offered / Withdrawn) |
| 19 other controller test classes (exams, evaluations, documents, conversion, offers, analytics, deposits, reminders, merit list + PDF, follow-ups, sections, waitlist, calls, waivers, TC checks, acceptance notices, campaigns, lead archive) | 102 | validation, 401 / 403, domain errors per endpoint |
| `AccessRuleControllerIntegrationTest` | 2 | endpoint → roles listing, 401 without a token |
| `AdmissionRulesUnitTest` (9 nested groups), `MeritRankingCalculatorTest`, `LeadConversionAnalyticsServiceTest`, `DocumentVaultStorageTest` | 26 | document check, PDF masking, section balancing, callbacks, waiver caps, board stub, CPA, trends, ranking, vault storage |
| **Total** | **240** | **0 failures, 0 errors** |

- [x] UI regression `node .agents/skills/test-ui/scripts/api-regression.mjs --service admissionservice`: **48 of 48 cases pass** (test stack, after `db.ps1 -Cleanup`; ADM-19 … ADM-48 walk one application through every step as its stakeholder)
- [x] Admission workflow runner: *Check permissions* green (56 endpoints, no staffing gaps), *Run whole workflow* 13 steps green and the waitlist step skipped by design
- [x] Student lifecycle runner: steps 1–6 (admission) green, enrolment with an empty conversion body using the application's date of birth
- [x] Browser: Add student with date of birth, gender and relationship → Review application shows them → *Enrol student* asks only for the section → the student profile shows the guardian as "Father · Primary contact"

## 6. Not yet implemented / known gaps

- **Board registries**: `StubEducationBoardGateway` answers deterministically; no CBSE / ICSE / IB / state API is integrated.
- **Notifications**: reminders and acceptance notices use the WhatsApp logging stub; offer letters are generated and stored but not e-mailed or sent by SMS. The payment page behind the offer link is out of scope.
- **Schedulers**: waitlist escalation and reminder schedulers are off by default (`WAITLIST_AUTO_ESCALATION_ENABLED`, `REMINDER_AUTO_DISPATCH_ENABLED`).
- **Add student**: the student photo, house, medical fields and emergency contact are entered on the form but not stored on the application (no fields); class placement happens at enrolment.
- **Schema**: tables come from Hibernate; `module2.sql` should gain the new tables before moving to Flyway.
- **Actor matrix**: where no backlog actor was granted (e.g. US-02013, US-02029), the capability is delivered through the roles that own the process; confirm with the business before go-live.
