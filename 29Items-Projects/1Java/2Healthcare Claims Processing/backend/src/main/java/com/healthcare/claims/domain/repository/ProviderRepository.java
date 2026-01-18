package com.healthcare.claims.domain.repository;

import com.healthcare.claims.domain.model.Provider;
import io.quarkus.hibernate.reactive.panache.PanacheRepositoryBase;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.UUID;

/**
 * Reactive repository for Provider entity operations.
 */
@ApplicationScoped
public class ProviderRepository implements PanacheRepositoryBase<Provider, UUID> {

    /**
     * Finds a provider by NPI number.
     */
    public Uni<Provider> findByNpi(String npi) {
        return find("npi", npi).firstResult();
    }

    /**
     * Finds a provider by tax ID.
     */
    public Uni<Provider> findByTaxId(String taxId) {
        return find("taxId", taxId).firstResult();
    }

    /**
     * Checks if a provider exists with the given NPI.
     */
    public Uni<Boolean> existsByNpi(String npi) {
        return count("npi", npi).map(count -> count > 0);
    }

    /**
     * Finds all in-network providers.
     */
    public Uni<List<Provider>> findInNetworkProviders() {
        return list("inNetwork = true and isActive = true");
    }

    /**
     * Finds providers by specialty.
     */
    public Uni<List<Provider>> findBySpecialty(String specialty) {
        return list("specialty", specialty);
    }

    /**
     * Finds providers flagged for fraud review.
     */
    public Uni<List<Provider>> findFraudFlaggedProviders() {
        return list("fraudRiskScore >= ?1", 0.7);
    }

    /**
     * Finds active providers.
     */
    public Uni<List<Provider>> findActiveProviders() {
        return list("isActive = true and credentialingStatus = 'ACTIVE'");
    }
}
