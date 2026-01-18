package com.healthcare.claims.domain.service;

import com.healthcare.claims.domain.model.Claim;
import com.healthcare.claims.domain.model.ClaimStatus;
import com.healthcare.claims.domain.repository.ClaimRepository;
import com.healthcare.claims.domain.repository.PatientRepository;
import com.healthcare.claims.domain.repository.ProviderRepository;
import com.healthcare.claims.infrastructure.kafka.ClaimEventProducer;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.UUID;

/**
 * Core service for claim processing operations.
 */
@ApplicationScoped
public class ClaimService {

    private static final Logger LOG = Logger.getLogger(ClaimService.class);

    @Inject
    ClaimRepository claimRepository;

    @Inject
    PatientRepository patientRepository;

    @Inject
    ProviderRepository providerRepository;

    @Inject
    ClaimEventProducer eventProducer;

    @Inject
    AdjudicationService adjudicationService;

    @Inject
    FraudDetectionService fraudDetectionService;

    /**
     * Submits a new claim for processing.
     */
    @WithTransaction
    public Uni<Claim> submitClaim(@Valid Claim claim) {
        LOG.infof("Submitting new claim for patient: %s", claim.getPatientId());

        return validateClaimEligibility(claim)
            .flatMap(valid -> {
                claim.setStatus(ClaimStatus.SUBMITTED);
                return claimRepository.persist(claim);
            })
            .flatMap(savedClaim -> {
                eventProducer.sendClaimSubmitted(savedClaim);
                return Uni.createFrom().item(savedClaim);
            })
            .onItem().invoke(c -> LOG.infof("Claim submitted successfully: %s", c.getClaimNumber()));
    }

    /**
     * Retrieves a claim by ID.
     */
    public Uni<Claim> getClaimById(UUID id) {
        return claimRepository.findById(id);
    }

    /**
     * Retrieves a claim by claim number.
     */
    public Uni<Claim> getClaimByNumber(String claimNumber) {
        return claimRepository.findByClaimNumber(claimNumber);
    }

    /**
     * Retrieves claims by status.
     */
    public Uni<List<Claim>> getClaimsByStatus(ClaimStatus status) {
        return claimRepository.findByStatus(status);
    }

    /**
     * Retrieves claims for a patient.
     */
    public Uni<List<Claim>> getClaimsForPatient(UUID patientId) {
        return claimRepository.findByPatientId(patientId);
    }

    /**
     * Retrieves paginated claims.
     */
    public Uni<List<Claim>> getClaimsPaginated(ClaimStatus status, int page, int size) {
        return claimRepository.findPaginated(status, page, size);
    }

    /**
     * Processes a claim through adjudication.
     */
    @WithTransaction
    public Uni<Claim> processClaim(UUID claimId) {
        LOG.infof("Processing claim: %s", claimId);

        return claimRepository.findById(claimId)
            .flatMap(claim -> {
                if (claim == null) {
                    return Uni.createFrom().failure(
                        new IllegalArgumentException("Claim not found: " + claimId)
                    );
                }
                return validateAndProcess(claim);
            });
    }

    /**
     * Updates claim status.
     */
    @WithTransaction
    public Uni<Claim> updateClaimStatus(UUID claimId, ClaimStatus newStatus, String notes) {
        return claimRepository.findById(claimId)
            .flatMap(claim -> {
                if (claim == null) {
                    return Uni.createFrom().failure(
                        new IllegalArgumentException("Claim not found: " + claimId)
                    );
                }
                claim.transitionTo(newStatus);
                if (notes != null) {
                    claim.setNotes(claim.getNotes() != null ?
                        claim.getNotes() + "\n" + notes : notes);
                }
                return claimRepository.persist(claim);
            })
            .onItem().invoke(claim -> {
                eventProducer.sendClaimStatusChanged(claim);
                LOG.infof("Claim %s status updated to %s", claim.getClaimNumber(), newStatus);
            });
    }

    /**
     * Approves a claim after review.
     */
    @WithTransaction
    public Uni<Claim> approveClaim(UUID claimId, String reviewedBy, String notes) {
        return claimRepository.findById(claimId)
            .flatMap(claim -> {
                if (claim == null) {
                    return Uni.createFrom().failure(
                        new IllegalArgumentException("Claim not found: " + claimId)
                    );
                }
                claim.transitionTo(ClaimStatus.APPROVED);
                claim.setReviewedBy(reviewedBy);
                claim.setReviewedAt(java.time.LocalDateTime.now());
                if (notes != null) {
                    claim.setNotes(claim.getNotes() != null ?
                        claim.getNotes() + "\n" + notes : notes);
                }
                return claimRepository.persist(claim);
            })
            .onItem().invoke(claim -> {
                eventProducer.sendClaimApproved(claim);
                LOG.infof("Claim %s approved by %s", claim.getClaimNumber(), reviewedBy);
            });
    }

    /**
     * Denies a claim.
     */
    @WithTransaction
    public Uni<Claim> denyClaim(UUID claimId, String reason, String reviewedBy) {
        return claimRepository.findById(claimId)
            .flatMap(claim -> {
                if (claim == null) {
                    return Uni.createFrom().failure(
                        new IllegalArgumentException("Claim not found: " + claimId)
                    );
                }
                claim.transitionTo(ClaimStatus.DENIED);
                claim.setDenialReason(reason);
                claim.setReviewedBy(reviewedBy);
                claim.setReviewedAt(java.time.LocalDateTime.now());
                return claimRepository.persist(claim);
            })
            .onItem().invoke(claim -> {
                eventProducer.sendClaimDenied(claim);
                LOG.infof("Claim %s denied: %s", claim.getClaimNumber(), reason);
            });
    }

    /**
     * Retrieves claims requiring human review.
     */
    public Uni<List<Claim>> getClaimsRequiringReview() {
        return claimRepository.findClaimsRequiringReview();
    }

    // Private helper methods

    private Uni<Boolean> validateClaimEligibility(Claim claim) {
        // Run sequentially to avoid Hibernate Reactive session conflicts
        return patientRepository.findById(claim.getPatientId())
            .flatMap(patient -> {
                if (patient == null) {
                    return Uni.createFrom().failure(
                        new IllegalArgumentException("Patient not found: " + claim.getPatientId())
                    );
                }
                if (!patient.isPolicyActive()) {
                    return Uni.createFrom().failure(
                        new IllegalArgumentException("Patient policy is not active")
                    );
                }
                return providerRepository.findById(claim.getProviderId());
            })
            .flatMap(provider -> {
                if (provider == null) {
                    return Uni.createFrom().failure(
                        new IllegalArgumentException("Provider not found: " + claim.getProviderId())
                    );
                }
                if (!provider.isEligibleForClaims()) {
                    return Uni.createFrom().failure(
                        new IllegalArgumentException("Provider is not eligible to submit claims")
                    );
                }
                return Uni.createFrom().item(true);
            });
    }

    private Uni<Claim> validateAndProcess(Claim claim) {
        claim.transitionTo(ClaimStatus.VALIDATING);

        return claimRepository.persist(claim)
            .flatMap(c -> {
                c.transitionTo(ClaimStatus.PENDING_ADJUDICATION);
                return claimRepository.persist(c);
            })
            .flatMap(c -> fraudDetectionService.scoreClaim(c))
            .flatMap(c -> {
                if (c.isFraudFlagged()) {
                    c.transitionTo(ClaimStatus.FLAGGED_FRAUD);
                    return claimRepository.persist(c);
                }
                return adjudicationService.adjudicateClaim(c);
            });
    }
}
