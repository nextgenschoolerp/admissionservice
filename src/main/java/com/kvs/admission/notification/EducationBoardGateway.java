package com.kvs.admission.notification;

import com.kvs.admission.enums.EducationBoard;
import com.kvs.admission.enums.TcVerificationResult;

import java.time.LocalDate;

/**
 * Lookup of transfer certificates in the issuing education board's registry (US-02017).
 */
public interface EducationBoardGateway {

    BoardLookupResult verifyTransferCertificate(EducationBoard board, String tcNumber, String candidateName, String previousSchoolName);

    record BoardLookupResult(TcVerificationResult result, String boardReference, String issuingSchool, LocalDate issueDate,
                             String message) {
    }
}
