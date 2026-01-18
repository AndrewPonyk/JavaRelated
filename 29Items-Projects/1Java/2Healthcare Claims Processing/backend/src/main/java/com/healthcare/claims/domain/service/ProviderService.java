package com.healthcare.claims.domain.service;

import com.healthcare.claims.domain.model.Provider;
import com.healthcare.claims.domain.repository.ProviderRepository;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.UUID;

/**
 * Service for provider management operations.
 */
@ApplicationScoped
public class ProviderService {

    private static final Logger LOG = Logger.getLogger(ProviderService.class);

    @Inject
    ProviderRepository providerRepository;

    /**
     * Creates a new provider.
     */
    @WithTransaction
    public Uni<Provider> createProvider(@Valid Provider provider) {
        LOG.infof("Creating provider with NPI: %s", provider.getNpi());

        return providerRepository.existsByNpi(provider.getNpi())
            .flatMap(exists -> {
                if (exists) {
                    return Uni.createFrom().failure(
                        new IllegalArgumentException("Provider with NPI already exists: " + provider.getNpi())
                    );
                }
                if (provider.getIsActive() == null) {
                    provider.setIsActive(true);
                }
                if (provider.getInNetwork() == null) {
                    provider.setInNetwork(true);
                }
                if (provider.getCredentialingStatus() == null) {
                    provider.setCredentialingStatus("ACTIVE");
                }
                return providerRepository.persist(provider);
            })
            .onItem().invoke(p -> LOG.infof("Provider created: %s", p.getId()));
    }

    /**
     * Retrieves a provider by ID.
     */
    public Uni<Provider> getProviderById(UUID id) {
        return providerRepository.findById(id);
    }

    /**
     * Retrieves a provider by NPI.
     */
    public Uni<Provider> getProviderByNpi(String npi) {
        return providerRepository.findByNpi(npi);
    }

    /**
     * Retrieves a provider by Tax ID.
     */
    public Uni<Provider> getProviderByTaxId(String taxId) {
        return providerRepository.findByTaxId(taxId);
    }

    /**
     * Retrieves all providers with pagination.
     */
    public Uni<List<Provider>> getAllProviders(int page, int size) {
        return providerRepository.findAll(Sort.descending("createdAt"))
            .page(Page.of(page, size))
            .list();
    }

    /**
     * Retrieves active providers.
     */
    public Uni<List<Provider>> getActiveProviders() {
        return providerRepository.findActiveProviders();
    }

    /**
     * Retrieves in-network providers.
     */
    public Uni<List<Provider>> getInNetworkProviders() {
        return providerRepository.findInNetworkProviders();
    }

    /**
     * Retrieves providers by specialty.
     */
    public Uni<List<Provider>> getProvidersBySpecialty(String specialty) {
        return providerRepository.findBySpecialty(specialty);
    }

    /**
     * Retrieves providers flagged for fraud review.
     */
    public Uni<List<Provider>> getFraudFlaggedProviders() {
        return providerRepository.findFraudFlaggedProviders();
    }

    /**
     * Searches providers by name.
     */
    public Uni<List<Provider>> searchProvidersByName(String name) {
        String searchPattern = "%" + name.toLowerCase() + "%";
        return providerRepository.list("lower(name) like ?1", searchPattern);
    }

    /**
     * Updates an existing provider.
     */
    @WithTransaction
    public Uni<Provider> updateProvider(UUID id, @Valid Provider updatedProvider) {
        LOG.infof("Updating provider: %s", id);

        return providerRepository.findById(id)
            .flatMap(existingProvider -> {
                if (existingProvider == null) {
                    return Uni.createFrom().failure(
                        new IllegalArgumentException("Provider not found: " + id)
                    );
                }

                // Check if NPI is being changed to one that already exists
                if (!existingProvider.getNpi().equals(updatedProvider.getNpi())) {
                    return providerRepository.existsByNpi(updatedProvider.getNpi())
                        .flatMap(exists -> {
                            if (exists) {
                                return Uni.createFrom().failure(
                                    new IllegalArgumentException("NPI already in use: " + updatedProvider.getNpi())
                                );
                            }
                            return updateProviderFields(existingProvider, updatedProvider);
                        });
                }

                return updateProviderFields(existingProvider, updatedProvider);
            })
            .onItem().invoke(p -> LOG.infof("Provider updated: %s", p.getId()));
    }

    /**
     * Updates a provider's fraud risk score.
     */
    @WithTransaction
    public Uni<Provider> updateFraudRiskScore(UUID id, Double score) {
        LOG.infof("Updating fraud risk score for provider: %s to %s", id, score);

        return providerRepository.findById(id)
            .flatMap(provider -> {
                if (provider == null) {
                    return Uni.createFrom().failure(
                        new IllegalArgumentException("Provider not found: " + id)
                    );
                }
                provider.setFraudRiskScore(score);
                return providerRepository.persist(provider);
            });
    }

    /**
     * Updates a provider's credentialing status.
     */
    @WithTransaction
    public Uni<Provider> updateCredentialingStatus(UUID id, String status) {
        LOG.infof("Updating credentialing status for provider: %s to %s", id, status);

        return providerRepository.findById(id)
            .flatMap(provider -> {
                if (provider == null) {
                    return Uni.createFrom().failure(
                        new IllegalArgumentException("Provider not found: " + id)
                    );
                }
                provider.setCredentialingStatus(status);
                return providerRepository.persist(provider);
            });
    }

    /**
     * Deactivates a provider.
     */
    @WithTransaction
    public Uni<Provider> deactivateProvider(UUID id) {
        LOG.infof("Deactivating provider: %s", id);

        return providerRepository.findById(id)
            .flatMap(provider -> {
                if (provider == null) {
                    return Uni.createFrom().failure(
                        new IllegalArgumentException("Provider not found: " + id)
                    );
                }
                provider.setIsActive(false);
                return providerRepository.persist(provider);
            })
            .onItem().invoke(p -> LOG.infof("Provider deactivated: %s", p.getId()));
    }

    /**
     * Reactivates a provider.
     */
    @WithTransaction
    public Uni<Provider> reactivateProvider(UUID id) {
        LOG.infof("Reactivating provider: %s", id);

        return providerRepository.findById(id)
            .flatMap(provider -> {
                if (provider == null) {
                    return Uni.createFrom().failure(
                        new IllegalArgumentException("Provider not found: " + id)
                    );
                }
                provider.setIsActive(true);
                return providerRepository.persist(provider);
            })
            .onItem().invoke(p -> LOG.infof("Provider reactivated: %s", p.getId()));
    }

    /**
     * Deletes a provider (hard delete).
     */
    @WithTransaction
    public Uni<Boolean> deleteProvider(UUID id) {
        LOG.infof("Deleting provider: %s", id);

        return providerRepository.deleteById(id)
            .onItem().invoke(deleted -> {
                if (deleted) {
                    LOG.infof("Provider deleted: %s", id);
                } else {
                    LOG.warnf("Provider not found for deletion: %s", id);
                }
            });
    }

    /**
     * Counts total providers.
     */
    public Uni<Long> countProviders() {
        return providerRepository.count();
    }

    /**
     * Counts in-network providers.
     */
    public Uni<Long> countInNetworkProviders() {
        return providerRepository.count("inNetwork = true and isActive = true");
    }

    private Uni<Provider> updateProviderFields(Provider existing, Provider updated) {
        existing.setNpi(updated.getNpi());
        existing.setName(updated.getName());
        existing.setSpecialty(updated.getSpecialty());
        existing.setTaxId(updated.getTaxId());
        if (updated.getInNetwork() != null) {
            existing.setInNetwork(updated.getInNetwork());
        }
        existing.setProviderType(updated.getProviderType());
        existing.setEmail(updated.getEmail());
        existing.setPhone(updated.getPhone());
        existing.setAddress(updated.getAddress());
        existing.setCity(updated.getCity());
        existing.setState(updated.getState());
        existing.setZipCode(updated.getZipCode());
        if (updated.getIsActive() != null) {
            existing.setIsActive(updated.getIsActive());
        }
        if (updated.getCredentialingStatus() != null) {
            existing.setCredentialingStatus(updated.getCredentialingStatus());
        }
        return providerRepository.persist(existing);
    }
}
