package com.healthcare.claims.domain.repository;

import com.healthcare.claims.domain.model.Claim;
import com.healthcare.claims.domain.model.ClaimStatus;
import io.quarkus.hibernate.reactive.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Reactive repository for Claim entity operations.
 */
@ApplicationScoped
public class ClaimRepository implements PanacheRepositoryBase<Claim, UUID> {

    /**
     * Finds a claim by its claim number.
     */
    public Uni<Claim> findByClaimNumber(String claimNumber) {
        return find("claimNumber", claimNumber).firstResult();
    }

    /**
     * Finds claims by status.
     */
    public Uni<List<Claim>> findByStatus(ClaimStatus status) {
        return list("status", Sort.by("createdAt").descending(), status);
    }

    /**
     * Finds claims by patient ID.
     */
    public Uni<List<Claim>> findByPatientId(UUID patientId) {
        return list("patientId", Sort.by("createdAt").descending(), patientId);
    }

    /**
     * Finds claims by provider ID.
     */
    public Uni<List<Claim>> findByProviderId(UUID providerId) {
        return list("providerId", Sort.by("createdAt").descending(), providerId);
    }

    /**
     * Finds claims requiring review (pending review or fraud flagged).
     */
    public Uni<List<Claim>> findClaimsRequiringReview() {
        return list("status in ?1",
            Sort.by("createdAt").ascending(),
            List.of(ClaimStatus.PENDING_REVIEW, ClaimStatus.FLAGGED_FRAUD));
    }

    /**
     * Finds claims by service date range.
     */
    public Uni<List<Claim>> findByServiceDateRange(LocalDate startDate, LocalDate endDate) {
        return list("serviceDate between ?1 and ?2",
            Sort.by("serviceDate").descending(),
            startDate, endDate);
    }

    /**
     * Finds claims with fraud score above threshold.
     */
    public Uni<List<Claim>> findHighFraudRiskClaims(Double threshold) {
        return list("fraudScore >= ?1",
            Sort.by("fraudScore").descending(),
            threshold);
    }

    /**
     * Counts claims by status.
     */
    public Uni<Long> countByStatus(ClaimStatus status) {
        return count("status", status);
    }

    /**
     * Finds paginated claims with optional filters.
     */
    public Uni<List<Claim>> findPaginated(ClaimStatus status, int page, int size) {
        if (status != null) {
            return find("status", Sort.by("createdAt").descending(), status)
                .page(Page.of(page, size))
                .list();
        }
        return findAll(Sort.by("createdAt").descending())
            .page(Page.of(page, size))
            .list();
    }

    /**
     * Finds claims eligible for auto-adjudication.
     */
    public Uni<List<Claim>> findEligibleForAutoAdjudication() {
        return list("status = ?1 and (fraudScore is null or fraudScore < 0.7) and amount <= 500",
            ClaimStatus.PENDING_ADJUDICATION);
    }
}
