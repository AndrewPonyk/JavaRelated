package com.healthcare.claims.domain.repository;

import com.healthcare.claims.domain.model.Patient;
import io.quarkus.hibernate.reactive.panache.PanacheRepositoryBase;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.UUID;

/**
 * Reactive repository for Patient entity operations.
 */
@ApplicationScoped
public class PatientRepository implements PanacheRepositoryBase<Patient, UUID> {

    /**
     * Finds a patient by member ID.
     */
    public Uni<Patient> findByMemberId(String memberId) {
        return find("memberId", memberId).firstResult();
    }

    /**
     * Finds a patient by policy number.
     */
    public Uni<Patient> findByPolicyNumber(String policyNumber) {
        return find("policyNumber", policyNumber).firstResult();
    }

    /**
     * Checks if a patient exists with the given member ID.
     */
    public Uni<Boolean> existsByMemberId(String memberId) {
        return count("memberId", memberId).map(count -> count > 0);
    }

    /**
     * Finds active patients only.
     */
    public Uni<java.util.List<Patient>> findActivePatients() {
        return list("isActive", true);
    }
}
