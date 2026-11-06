package com.example.paygenix.repository;

import com.example.paygenix.entity.ProblemReport;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProblemReportRepository extends JpaRepository<ProblemReport, Long> {

    org.springframework.data.domain.Slice<ProblemReport> findByStatus(com.example.paygenix.enums.ReportStatus status,
                                                                       org.springframework.data.domain.Pageable pageable);
}
