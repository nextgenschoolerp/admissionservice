package com.kvs.admission.controller;

import com.kvs.admission.service.ApplicantDocumentService;
import com.kvs.core.tenancy.TenantContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

/**
 * Service-to-service: where a verified applicant document's file is in the shared store, so studentservice can copy it to the
 * student's profile on enrolment. Needs the shared internal key (kvs.internal.key), like the other internal endpoints.
 */
@RestController
@RequestMapping("/internal/v1/admissions")
public class InternalApplicantDocumentController {

    private final ApplicantDocumentService documentService;
    private final String internalKey;

    @Autowired
    public InternalApplicantDocumentController(ApplicantDocumentService documentService,
                                               @Value("${kvs.internal.key:schoolerp-dev-internal-key}") String internalKey) {
        this.documentService = documentService;
        this.internalKey = internalKey;
    }

    @GetMapping("/documents/{documentId}/storage")
    public ResponseEntity<ApplicantDocumentService.StorageInfo> storage(
            @RequestHeader(value = "X-Internal-Key", required = false) String key,
            @PathVariable("documentId") UUID documentId,
            @RequestParam("tenantId") String tenantId) {
        if (key == null || !MessageDigest.isEqual(key.getBytes(StandardCharsets.UTF_8), internalKey.getBytes(StandardCharsets.UTF_8))) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(TenantContext.callAs(tenantId, () -> documentService.storageInfo(tenantId, documentId)));
    }
}
