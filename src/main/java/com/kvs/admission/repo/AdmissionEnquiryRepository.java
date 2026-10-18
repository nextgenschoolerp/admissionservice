package com.kvs.admission.repo;

import com.kvs.admission.entity.AdmissionEnquiry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AdmissionEnquiryRepository extends JpaRepository<AdmissionEnquiry, Long> {

    Optional<AdmissionEnquiry> findByApplicationNumber(String applicationNumber);

    Optional<AdmissionEnquiry> findByEnquiryId(UUID enquiryId);

    List<AdmissionEnquiry> findByEnquiryIdIn(Collection<UUID> enquiryIds);

    List<AdmissionEnquiry> findByTenantIdAndBranchId(String tenantId, String branchId);

    List<AdmissionEnquiry> findByTenantIdAndBranchIdAndArchivedAtIsNull(String tenantId, String branchId);

    List<AdmissionEnquiry> findByTenantIdAndBranchIdAndAcademicYearIdAndTargetGradeIdAndEnquiryStatusInAndArchivedAtIsNull(
            String tenantId, String branchId, String academicYearId, String targetGradeId, Collection<String> statuses);

    List<AdmissionEnquiry> findByTenantIdAndEnquiryStatusInAndArchivedAtIsNull(String tenantId, Collection<String> statuses);

    List<AdmissionEnquiry> findByTenantIdAndBranchIdAndEnquiryStatusInAndArchivedAtIsNull(
            String tenantId, String branchId, Collection<String> statuses);

    List<AdmissionEnquiry> findByTenantIdAndCampaignCodeIn(String tenantId, Collection<String> campaignCodes);

    List<AdmissionEnquiry> findByTenantIdAndBranchIdAndEnquiryStatus(String tenantId, String branchId, String enquiryStatus);

    List<AdmissionEnquiry> findByTenantIdAndBranchIdAndAcademicYearIdAndTargetGradeId(
            String tenantId, String branchId, String academicYearId, String targetGradeId);

    Optional<AdmissionEnquiry> findByGuardianPhoneAndTenantId(String guardianPhone, String tenantId);

    Optional<AdmissionEnquiry> findByGuardianEmailAndTenantId(String guardianEmail, String tenantId);

    /**
     * Lead counts per referral source and pipeline status for a tenant (US-02007).
     * Each row: [referralSource, enquiryStatus, count].
     */
    @Query("select e.referralSource, e.enquiryStatus, count(e) from AdmissionEnquiry e "
            + "where e.tenantId = :tenantId and e.createdAt >= :from and e.createdAt < :to "
            + "group by e.referralSource, e.enquiryStatus")
    List<Object[]> countByReferralSourceAndStatus(@Param("tenantId") String tenantId,
                                                  @Param("from") Instant from,
                                                  @Param("to") Instant to);

    /**
     * Same as {@link #countByReferralSourceAndStatus} restricted to one branch.
     */
    @Query("select e.referralSource, e.enquiryStatus, count(e) from AdmissionEnquiry e "
            + "where e.tenantId = :tenantId and e.branchId = :branchId "
            + "and e.createdAt >= :from and e.createdAt < :to "
            + "group by e.referralSource, e.enquiryStatus")
    List<Object[]> countByReferralSourceAndStatusForBranch(@Param("tenantId") String tenantId,
                                                           @Param("branchId") String branchId,
                                                           @Param("from") Instant from,
                                                           @Param("to") Instant to);
}
