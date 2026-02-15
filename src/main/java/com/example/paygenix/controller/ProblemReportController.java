package com.example.paygenix.controller;

import com.example.paygenix.dto.ApiResponse;
import com.example.paygenix.dto.request.CreateProblemReportRequest;
import com.example.paygenix.dto.response.CreateProblemReportResponse;
import com.example.paygenix.dto.response.ProblemReportResponse;
import com.example.paygenix.entity.ProblemReport;
import com.example.paygenix.service.ProblemReportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ProblemReportController {

    private final ProblemReportService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @ExceptionHandler
    public ResponseEntity<ApiResponse<CreateProblemReportResponse>> create(
            @RequestBody @Valid CreateProblemReportRequest request
    ) {
        ProblemReport saved = service.create(request);
        CreateProblemReportResponse c = new CreateProblemReportResponse(
                saved.getId(),
                saved.getStatus().name(),
                saved.getCreatedAt());
        return ResponseEntity.ok(ApiResponse.success(c));
    }

    @ExceptionHandler
    @GetMapping
    public ResponseEntity<ApiResponse<List<ProblemReportResponse>>>  getAll(){
        return ResponseEntity.ok(ApiResponse.build(
                HttpStatus.OK,"Successfully fethed all user's products",
                service.getAllReports()
        ));
    }
}

