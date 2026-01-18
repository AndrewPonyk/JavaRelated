package com.healthcare.claims.domain.service;

import com.healthcare.claims.domain.model.Provider;
import com.healthcare.claims.domain.repository.ProviderRepository;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ProviderService.
 */
@QuarkusTest
class ProviderServiceTest {

    @Inject
    ProviderService providerService;

    @InjectMock
    ProviderRepository providerRepository;

    private Provider createTestProvider() {
        return Provider.builder()
            .id(UUID.randomUUID())
            .npi("9876543210")
            .name("Test Medical Center")
            .specialty("Internal Medicine")
            .taxId("98-7654321")
            .inNetwork(true)
            .providerType("HOSPITAL")
            .email("test@medcenter.com")
            .phone("555-9999")
            .isActive(true)
            .credentialingStatus("ACTIVE")
            .build();
    }

    @Test
    @RunOnVertxContext
    @DisplayName("Should create new provider successfully")
    void shouldCreateNewProviderSuccessfully(UniAsserter asserter) {
        Provider testProvider = createTestProvider();

        when(providerRepository.existsByNpi(anyString()))
            .thenReturn(Uni.createFrom().item(false));
        when(providerRepository.persist(any(Provider.class)))
            .thenAnswer(inv -> {
                Provider p = inv.getArgument(0, Provider.class);
                return Uni.createFrom().item(p);
            });

        asserter.assertThat(
            () -> providerService.createProvider(testProvider),
            result -> {
                assertThat(result).isNotNull();
                assertThat(result.getNpi()).isEqualTo("9876543210");
                assertThat(result.getIsActive()).isTrue();
            }
        );
    }

    @Test
    @RunOnVertxContext
    @DisplayName("Should reject duplicate NPI")
    void shouldRejectDuplicateNpi(UniAsserter asserter) {
        Provider testProvider = createTestProvider();

        when(providerRepository.existsByNpi(anyString()))
            .thenReturn(Uni.createFrom().item(true));

        asserter.assertFailedWith(
            () -> providerService.createProvider(testProvider),
            throwable -> assertThat(throwable.getMessage()).contains("already exists")
        );
    }

    @Test
    @RunOnVertxContext
    @DisplayName("Should retrieve provider by ID")
    void shouldRetrieveProviderById(UniAsserter asserter) {
        Provider testProvider = createTestProvider();

        when(providerRepository.findById(testProvider.getId()))
            .thenReturn(Uni.createFrom().item(testProvider));

        asserter.assertThat(
            () -> providerService.getProviderById(testProvider.getId()),
            result -> {
                assertThat(result).isNotNull();
                assertThat(result.getId()).isEqualTo(testProvider.getId());
            }
        );
    }

    @Test
    @RunOnVertxContext
    @DisplayName("Should retrieve provider by NPI")
    void shouldRetrieveProviderByNpi(UniAsserter asserter) {
        Provider testProvider = createTestProvider();

        when(providerRepository.findByNpi("9876543210"))
            .thenReturn(Uni.createFrom().item(testProvider));

        asserter.assertThat(
            () -> providerService.getProviderByNpi("9876543210"),
            result -> {
                assertThat(result).isNotNull();
                assertThat(result.getNpi()).isEqualTo("9876543210");
            }
        );
    }

    @Test
    @RunOnVertxContext
    @DisplayName("Should update provider successfully")
    void shouldUpdateProviderSuccessfully(UniAsserter asserter) {
        Provider testProvider = createTestProvider();
        Provider updatedProvider = Provider.builder()
            .npi("9876543210")
            .name("Updated Medical Center")
            .specialty("Family Medicine")
            .inNetwork(true)
            .isActive(true)
            .build();

        when(providerRepository.findById(testProvider.getId()))
            .thenReturn(Uni.createFrom().item(testProvider));
        when(providerRepository.persist(any(Provider.class)))
            .thenAnswer(inv -> {
                Provider p = inv.getArgument(0, Provider.class);
                return Uni.createFrom().item(p);
            });

        asserter.assertThat(
            () -> providerService.updateProvider(testProvider.getId(), updatedProvider),
            result -> {
                assertThat(result.getName()).isEqualTo("Updated Medical Center");
                assertThat(result.getSpecialty()).isEqualTo("Family Medicine");
            }
        );
    }

    @Test
    @RunOnVertxContext
    @DisplayName("Should update fraud risk score successfully")
    void shouldUpdateFraudRiskScoreSuccessfully(UniAsserter asserter) {
        Provider testProvider = createTestProvider();

        when(providerRepository.findById(testProvider.getId()))
            .thenReturn(Uni.createFrom().item(testProvider));
        when(providerRepository.persist(any(Provider.class)))
            .thenAnswer(inv -> {
                Provider p = inv.getArgument(0, Provider.class);
                return Uni.createFrom().item(p);
            });

        asserter.assertThat(
            () -> providerService.updateFraudRiskScore(testProvider.getId(), 0.75),
            result -> {
                assertThat(result.getFraudRiskScore()).isEqualTo(0.75);
                assertThat(result.isFraudFlagged()).isTrue();
            }
        );
    }

    @Test
    @RunOnVertxContext
    @DisplayName("Should update credentialing status successfully")
    void shouldUpdateCredentialingStatusSuccessfully(UniAsserter asserter) {
        Provider testProvider = createTestProvider();

        when(providerRepository.findById(testProvider.getId()))
            .thenReturn(Uni.createFrom().item(testProvider));
        when(providerRepository.persist(any(Provider.class)))
            .thenAnswer(inv -> {
                Provider p = inv.getArgument(0, Provider.class);
                return Uni.createFrom().item(p);
            });

        asserter.assertThat(
            () -> providerService.updateCredentialingStatus(testProvider.getId(), "SUSPENDED"),
            result -> {
                assertThat(result.getCredentialingStatus()).isEqualTo("SUSPENDED");
                assertThat(result.isEligibleForClaims()).isFalse();
            }
        );
    }

    @Test
    @RunOnVertxContext
    @DisplayName("Should deactivate provider successfully")
    void shouldDeactivateProviderSuccessfully(UniAsserter asserter) {
        Provider testProvider = createTestProvider();

        when(providerRepository.findById(testProvider.getId()))
            .thenReturn(Uni.createFrom().item(testProvider));
        when(providerRepository.persist(any(Provider.class)))
            .thenAnswer(inv -> {
                Provider p = inv.getArgument(0, Provider.class);
                return Uni.createFrom().item(p);
            });

        asserter.assertThat(
            () -> providerService.deactivateProvider(testProvider.getId()),
            result -> assertThat(result.getIsActive()).isFalse()
        );
    }

    @Test
    @RunOnVertxContext
    @DisplayName("Should get in-network providers")
    void shouldGetInNetworkProviders(UniAsserter asserter) {
        Provider testProvider = createTestProvider();
        List<Provider> inNetworkProviders = List.of(testProvider);

        when(providerRepository.findInNetworkProviders())
            .thenReturn(Uni.createFrom().item(inNetworkProviders));

        asserter.assertThat(
            () -> providerService.getInNetworkProviders(),
            result -> {
                assertThat(result).hasSize(1);
                assertThat(result.get(0).getInNetwork()).isTrue();
            }
        );
    }

    @Test
    @RunOnVertxContext
    @DisplayName("Should get providers by specialty")
    void shouldGetProvidersBySpecialty(UniAsserter asserter) {
        Provider testProvider = createTestProvider();
        List<Provider> specialists = List.of(testProvider);

        when(providerRepository.findBySpecialty("Internal Medicine"))
            .thenReturn(Uni.createFrom().item(specialists));

        asserter.assertThat(
            () -> providerService.getProvidersBySpecialty("Internal Medicine"),
            result -> {
                assertThat(result).hasSize(1);
                assertThat(result.get(0).getSpecialty()).isEqualTo("Internal Medicine");
            }
        );
    }

    @Test
    @RunOnVertxContext
    @DisplayName("Should get fraud flagged providers")
    void shouldGetFraudFlaggedProviders(UniAsserter asserter) {
        Provider testProvider = createTestProvider();
        testProvider.setFraudRiskScore(0.85);
        List<Provider> flaggedProviders = List.of(testProvider);

        when(providerRepository.findFraudFlaggedProviders())
            .thenReturn(Uni.createFrom().item(flaggedProviders));

        asserter.assertThat(
            () -> providerService.getFraudFlaggedProviders(),
            result -> {
                assertThat(result).hasSize(1);
                assertThat(result.get(0).isFraudFlagged()).isTrue();
            }
        );
    }

    @Test
    @RunOnVertxContext
    @DisplayName("Should delete provider successfully")
    void shouldDeleteProviderSuccessfully(UniAsserter asserter) {
        Provider testProvider = createTestProvider();

        when(providerRepository.deleteById(testProvider.getId()))
            .thenReturn(Uni.createFrom().item(true));

        asserter.assertThat(
            () -> providerService.deleteProvider(testProvider.getId()),
            result -> assertThat(result).isTrue()
        );
    }

    @Test
    @DisplayName("Should verify eligibility rules correctly")
    void shouldVerifyEligibilityRulesCorrectly() {
        Provider testProvider = createTestProvider();
        testProvider.setIsActive(true);
        testProvider.setCredentialingStatus("ACTIVE");
        testProvider.setFraudRiskScore(0.1);

        assertThat(testProvider.isEligibleForClaims()).isTrue();

        testProvider.setFraudRiskScore(0.6);
        assertThat(testProvider.isEligibleForClaims()).isFalse();
    }
}
