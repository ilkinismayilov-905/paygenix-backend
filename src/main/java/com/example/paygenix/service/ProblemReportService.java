package com.example.paygenix.service;

import com.example.paygenix.dto.request.CreateProblemReportRequest;
import com.example.paygenix.dto.response.ProblemReportResponse;
import com.example.paygenix.entity.ProblemReport;
import com.example.paygenix.entity.User;
import com.example.paygenix.exceptions.ApiException;
import com.example.paygenix.exceptions.InvalidReportCategoryException;
import com.example.paygenix.exceptions.InvalidReportMessageException;
import com.example.paygenix.exceptions.UserNotLoginException;
import com.example.paygenix.repository.ProblemReportRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ProblemReportService {

    private final ProblemReportRepository repository;

    public ProblemReportService(ProblemReportRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public ProblemReport create(CreateProblemReportRequest req) {

        if (req == null) throw ApiException.validation("Request body is required");
        if (req.category() == null) throw new InvalidReportCategoryException();
        if (req.message() == null || req.message().isBlank()) throw new InvalidReportMessageException();

        ProblemReport report = new ProblemReport(
                getCurrentUser().getId(),
                req.category(),
                req.message().trim(),
                req.referenceId() == null ? null : req.referenceId().trim()
        );
        log.info("Creating {} problem report for user {}", req.category(), report.getUserId());
        return repository.save(report);
    }

    @Transactional(readOnly = true)
    public List<ProblemReportResponse> getAllReports() {
        log.info("Retrieving all problem reports");
        List<ProblemReport> reports = repository.findAll();
        return reports
                .stream()
                .map(this::mapper)
                .collect(Collectors.toList());
    }


    public User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal()))
            throw new UserNotLoginException("İstifadəçi Login olmayıb");

        return (User) authentication.getPrincipal();
    }

    private ProblemReportResponse mapper(ProblemReport report) {
        return new ProblemReportResponse(
                report.getId(),
                report.getUserId(),
                report.getCategory(),
                report.getMessage(),
                report.getStatus(),
                report.getCreatedAt()
        );
    }
}
