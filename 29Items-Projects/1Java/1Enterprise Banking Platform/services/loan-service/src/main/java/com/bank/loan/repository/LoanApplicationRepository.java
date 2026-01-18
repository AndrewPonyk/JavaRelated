package com.bank.loan.repository;

import com.bank.loan.model.LoanApplication;
import com.bank.loan.model.LoanApplication.ApplicationStatus;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Reactive repository for LoanApplication entity.
 */
@Repository
public interface LoanApplicationRepository extends R2dbcRepository<LoanApplication, UUID> {

    Mono<LoanApplication> findByApplicationNumber(String applicationNumber);

    Flux<LoanApplication> findByApplicantId(UUID applicantId);

    Flux<LoanApplication> findByStatus(ApplicationStatus status);

    @Query("SELECT * FROM loan_applications WHERE status = 'UNDER_REVIEW' ORDER BY created_at ASC")
    Flux<LoanApplication> findPendingReview();

    @Query("SELECT * FROM loan_applications WHERE status = 'DOCUMENTS_REQUIRED' AND applicant_id = :applicantId")
    Flux<LoanApplication> findDocumentsRequiredByApplicant(UUID applicantId);

    Mono<Long> countByStatus(ApplicationStatus status);

    Mono<Boolean> existsByApplicationNumber(String applicationNumber);
}
