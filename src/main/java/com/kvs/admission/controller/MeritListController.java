package com.kvs.admission.controller;

import com.kvs.admission.config.AdmissionPerms;
import com.kvs.admission.dto.GenerateMeritListRequest;
import com.kvs.admission.dto.MeritListResponse;
import com.kvs.admission.service.MeritListService;
import com.kvs.core.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Controller for candidate merit lists.
 * Covers user story:
 * US-02010: Rank candidates automatically based on weighted entrance test scores
 * Actor: Admission Officer (US-02050, US-02090; Finance Cashier in US-02030 / US-02070 not granted)
 * US-02012: Export merit list rankings to PDF for public noticeboard display
 * Actor: Entrance Exam Evaluator (US-02012, US-02052, US-02092), School Principal (US-02032, US-02072)
 */
@RestController
@Slf4j
@RequestMapping("/api/v1/admissions/merit-lists")
public class MeritListController {

    @Autowired
    private MeritListService meritListService;

    /**
     * Rank candidates by weighted entrance test and interview scores (US-02010).
     *
     * @param request validated ranking request
     * @return 201 Created with the merit list
     */
    @PostMapping
    @PreAuthorize(AdmissionPerms.WAITLISTS_MANAGE)
    public ResponseEntity<ApiResponse<MeritListResponse>> generateMeritList(
            @Valid @RequestBody GenerateMeritListRequest request) {
        log.info("Request to generate merit list for grade {} / year {}",
                request.getTargetGradeId(), request.getAcademicYearId());
        MeritListResponse response = meritListService.generateMeritList(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Merit list generated successfully", response));
    }

    /**
     * List the merit lists of a campus, newest first.
     *
     * @param tenantId the school
     * @param branchId the campus
     * @return 200 OK with the merit lists
     */
    @GetMapping
    @PreAuthorize(AdmissionPerms.MERIT_LISTS_VIEW)
    public ResponseEntity<ApiResponse<List<MeritListResponse>>> listMeritLists(@RequestParam("tenantId") String tenantId,
                                                                               @RequestParam("branchId") String branchId) {
        log.info("Request to list merit lists for tenant: {}, branch: {}", tenantId, branchId);
        return ResponseEntity.ok(ApiResponse.success(meritListService.listMeritLists(tenantId, branchId)));
    }

    /**
     * Retrieve a merit list.
     *
     * @param meritListId the merit list UUID
     * @return 200 OK with the ranked entries
     */
    @GetMapping("/{meritListId}")
    @PreAuthorize(AdmissionPerms.MERIT_LISTS_VIEW)
    public ResponseEntity<ApiResponse<MeritListResponse>> getMeritList(@PathVariable("meritListId") UUID meritListId) {
        log.info("Request to fetch merit list: {}", meritListId);
        return ResponseEntity.ok(ApiResponse.success(meritListService.getMeritList(meritListId)));
    }

    /**
     * Export a merit list as a noticeboard PDF (US-02012). Names are shown as "First L." unless fullNames=true.
     *
     * @param meritListId the merit list UUID
     * @param fullNames   print full candidate names
     * @return 200 OK with application/pdf
     */
    // No "produces" here: error responses must still be able to render as JSON
    @GetMapping("/{meritListId}/pdf")
    @PreAuthorize(AdmissionPerms.MERIT_LISTS_2_VIEW)
    public ResponseEntity<byte[]> exportPdf(@PathVariable("meritListId") UUID meritListId,
                                            @RequestParam(value = "fullNames", defaultValue = "false") boolean fullNames) {
        log.info("Request to export merit list {} as PDF (fullNames={})", meritListId, fullNames);
        MeritListService.PdfExport export = meritListService.exportPdf(meritListId, fullNames);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(export.fileName()).build().toString())
                .body(export.content());
    }
}
