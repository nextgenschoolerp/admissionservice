# Task Execution Record: Module 02 — Admission Management & Lead CRM (US-02002 – US-02010)

Execution of skill [`brd-to-api`](../.agents/skills/brd-to-api/SKILL.md) covering **US-02002 through US-02010** from [`School_ERP_Module_02_Admissions_CRM_1000.md`](../story/module2/School_ERP_Module_02_Admissions_CRM_1000.md).

---

## 1. Input Specification & Target Microservice Resolution

- **Module**: Module 02 — Admission Management, Lead CRM & Student Recruitment Pipeline
- **BRD Source File**: `story/module2/School_ERP_Module_02_Admissions_CRM_1000.md`
- **Target Microservice**: `admissionservice` (existing; US-02001 already implemented here)
- **Base Package**: `com.kvs.admission`
- **Server Port**: `8083`, the value already in `application.yml`. The skill table says 8082; left unchanged.
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
- [x] Step 7: Postman collections `postman/School_ERP_US-02002 … US-02010.postman_collection.json` (v2.1, positive + negative, `pm.test` assertions, ID capture)
- [x] Step 8: Wiki pages `wiki/api-docs/US-02002 … US-02010-*-api.md` (business rules, schemas, error dictionary, Mermaid sequence)

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

Postman collections were validated as JSON but **not executed** against a running service.

## 6. Known Gaps / Follow-ups

- **WhatsApp**: `LoggingWhatsAppGateway` is a stub. A real provider needs a `WhatsAppGateway` bean.
- **Offer letters**: content and payment link are generated and stored, but not emailed or SMSed. The payment page behind the link is out of scope (Module 04 finance).
- **Student Master**: `student_master_records` is the admission-side hand-off. The Module 03 `studentservice` does not exist yet to consume it.
- **Schema**: tables are created by Hibernate (`ddl-auto: create`). `module2.sql` should be updated with the new tables before moving to Flyway.
