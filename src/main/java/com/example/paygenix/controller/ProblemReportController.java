package com.example.paygenix.controller;

import com.example.paygenix.dto.ApiResponseData;
import com.example.paygenix.dto.request.CreateProblemReportRequest;
import com.example.paygenix.dto.response.CreateProblemReportResponse;
import com.example.paygenix.dto.response.ProblemReportResponse;
import com.example.paygenix.entity.ProblemReport;
import com.example.paygenix.service.ProblemReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Problem Reporting", description = "APIs for submitting and viewing user problem reports/tickets")
@SecurityRequirement(name = "bearerAuth")
public class ProblemReportController {

    private final ProblemReportService service;

    @Operation(
            summary = "Create a problem report",
            description = "Submits a new issue or feedback report to the support system."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Problem report created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid report data"),
            @ApiResponse(responseCode = "401", description = "Unauthorized access")
    })
    @PostMapping
    public ResponseEntity<ApiResponseData<CreateProblemReportResponse>> createProblemReport(
            @RequestBody @Valid CreateProblemReportRequest request
    ) {
        log.info("Creating a new problem report with category: {}", request.category());
        ProblemReport saved = service.create(request);

        CreateProblemReportResponse response = new CreateProblemReportResponse(
                saved.getId(),
                saved.getStatus().name(),
                saved.getCreatedAt()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponseData.build(
                HttpStatus.CREATED,
                "Problem report submitted successfully",
                response
        ));
    }

    @Operation(
            summary = "Get all problem reports",
            description = "Retrieves a list of all problem reports submitted by the user."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Reports retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized access")
    })
    @PreAuthorize("hasRole('ADMIN_ROLE')")
    @GetMapping
    public ResponseEntity<ApiResponseData<List<ProblemReportResponse>>> getAllReports() {
        log.info("Fetching all problem reports (admin)");
        List<ProblemReportResponse> reports = service.getAllReports();

        return ResponseEntity.ok(ApiResponseData.build(
                HttpStatus.OK,
                "Successfully fetched all problem reports",
                reports
        ));
    }
}