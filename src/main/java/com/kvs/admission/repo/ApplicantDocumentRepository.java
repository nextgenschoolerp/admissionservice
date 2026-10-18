package com.kvs.admission.repo;

import com.kvs.admission.entity.ApplicantDocument;
import com.kvs.admission.enums.DocumentType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApplicantDocumentRepository extends JpaRepository<ApplicantDocument, Long> {

    Optional<ApplicantDocument> findByDocumentId(UUID documentId);

    Optional<ApplicantDocument> findByEnquiryIdAndDocumentType(UUID enquiryId, DocumentType documentType);

    List<ApplicantDocument> findByEnquiryIdOrderByDocumentTypeAsc(UUID enquiryId);

    List<ApplicantDocument> findByEnquiryIdIn(Collection<UUID> enquiryIds);
}
