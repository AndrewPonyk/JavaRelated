package com.healthcare.claims.infrastructure.seed;

import com.healthcare.claims.domain.model.*;
import com.healthcare.claims.domain.repository.ClaimRepository;
import com.healthcare.claims.domain.repository.PatientRepository;
import com.healthcare.claims.domain.repository.ProviderRepository;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.quarkus.runtime.StartupEvent;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Random;

/**
 * Seeds the database with test data in development mode.
 */
@ApplicationScoped
public class DataSeeder {

    private static final Logger LOG = Logger.getLogger(DataSeeder.class);
    private static final Random random = new Random(42);

    @Inject
    PatientRepository patientRepository;

    @Inject
    ProviderRepository providerRepository;

    @Inject
    ClaimRepository claimRepository;

    @ConfigProperty(name = "claims.seed-data.enabled", defaultValue = "false")
    boolean seedDataEnabled;

    void onStart(@Observes StartupEvent event) {
        if (seedDataEnabled) {
            LOG.info("Seed data is enabled, checking if seeding is needed...");
            seedData()
                .subscribe().with(
                    result -> LOG.info("Data seeding completed"),
                    error -> LOG.errorf(error, "Failed to seed data")
                );
        }
    }

    @WithTransaction
    public Uni<Void> seedData() {
        return patientRepository.count()
            .flatMap(count -> {
                if (count > 0) {
                    LOG.info("Database already has data, skipping seeding");
                    return Uni.createFrom().voidItem();
                }
                LOG.info("Seeding database with test data...");
                return seedPatients()
                    .flatMap(patients -> seedProviders()
                        .flatMap(providers -> seedClaims(patients, providers)));
            });
    }

    private Uni<List<Patient>> seedPatients() {
        List<Patient> patients = List.of(
            Patient.builder()
                .memberId("MEM001")
                .firstName("John")
                .lastName("Smith")
                .dateOfBirth(LocalDate.of(1985, 3, 15))
                .policyNumber("POL-2024-001")
                .groupNumber("GRP-100")
                .policyStartDate(LocalDate.of(2024, 1, 1))
                .policyEndDate(LocalDate.of(2024, 12, 31))
                .email("john.smith@email.com")
                .phone("555-0101")
                .address("123 Main Street")
                .city("New York")
                .state("NY")
                .zipCode("10001")
                .isActive(true)
                .build(),
            Patient.builder()
                .memberId("MEM002")
                .firstName("Sarah")
                .lastName("Johnson")
                .dateOfBirth(LocalDate.of(1990, 7, 22))
                .policyNumber("POL-2024-002")
                .groupNumber("GRP-100")
                .policyStartDate(LocalDate.of(2024, 1, 1))
                .policyEndDate(LocalDate.of(2024, 12, 31))
                .email("sarah.johnson@email.com")
                .phone("555-0102")
                .address("456 Oak Avenue")
                .city("Los Angeles")
                .state("CA")
                .zipCode("90001")
                .isActive(true)
                .build(),
            Patient.builder()
                .memberId("MEM003")
                .firstName("Michael")
                .lastName("Williams")
                .dateOfBirth(LocalDate.of(1978, 11, 8))
                .policyNumber("POL-2024-003")
                .groupNumber("GRP-200")
                .policyStartDate(LocalDate.of(2024, 1, 1))
                .policyEndDate(LocalDate.of(2024, 12, 31))
                .email("michael.williams@email.com")
                .phone("555-0103")
                .address("789 Pine Road")
                .city("Chicago")
                .state("IL")
                .zipCode("60601")
                .isActive(true)
                .build(),
            Patient.builder()
                .memberId("MEM004")
                .firstName("Emily")
                .lastName("Brown")
                .dateOfBirth(LocalDate.of(1995, 5, 30))
                .policyNumber("POL-2024-004")
                .groupNumber("GRP-200")
                .policyStartDate(LocalDate.of(2024, 1, 1))
                .policyEndDate(LocalDate.of(2024, 12, 31))
                .email("emily.brown@email.com")
                .phone("555-0104")
                .address("321 Elm Street")
                .city("Houston")
                .state("TX")
                .zipCode("77001")
                .isActive(true)
                .build(),
            Patient.builder()
                .memberId("MEM005")
                .firstName("David")
                .lastName("Davis")
                .dateOfBirth(LocalDate.of(1982, 9, 12))
                .policyNumber("POL-2024-005")
                .groupNumber("GRP-300")
                .policyStartDate(LocalDate.of(2024, 1, 1))
                .policyEndDate(LocalDate.of(2024, 12, 31))
                .email("david.davis@email.com")
                .phone("555-0105")
                .address("654 Maple Lane")
                .city("Phoenix")
                .state("AZ")
                .zipCode("85001")
                .isActive(true)
                .build()
        );

        return patientRepository.persist(patients)
            .map(v -> patients)
            .onItem().invoke(p -> LOG.infof("Seeded %d patients", p.size()));
    }

    private Uni<List<Provider>> seedProviders() {
        List<Provider> providers = List.of(
            Provider.builder()
                .npi("1234567890")
                .name("City General Hospital")
                .specialty("General Medicine")
                .taxId("12-3456789")
                .inNetwork(true)
                .providerType("HOSPITAL")
                .email("info@citygeneral.com")
                .phone("555-1001")
                .address("100 Hospital Drive")
                .city("New York")
                .state("NY")
                .zipCode("10002")
                .isActive(true)
                .credentialingStatus("ACTIVE")
                .fraudRiskScore(0.1)
                .build(),
            Provider.builder()
                .npi("2345678901")
                .name("Dr. Jennifer Martinez, MD")
                .specialty("Internal Medicine")
                .taxId("23-4567890")
                .inNetwork(true)
                .providerType("PHYSICIAN")
                .email("dr.martinez@clinic.com")
                .phone("555-1002")
                .address("200 Medical Center Blvd")
                .city("Los Angeles")
                .state("CA")
                .zipCode("90002")
                .isActive(true)
                .credentialingStatus("ACTIVE")
                .fraudRiskScore(0.05)
                .build(),
            Provider.builder()
                .npi("3456789012")
                .name("Midwest Radiology Associates")
                .specialty("Radiology")
                .taxId("34-5678901")
                .inNetwork(true)
                .providerType("DIAGNOSTIC_CENTER")
                .email("contact@midwestradiology.com")
                .phone("555-1003")
                .address("300 Imaging Way")
                .city("Chicago")
                .state("IL")
                .zipCode("60602")
                .isActive(true)
                .credentialingStatus("ACTIVE")
                .fraudRiskScore(0.15)
                .build(),
            Provider.builder()
                .npi("4567890123")
                .name("Texas Orthopedic Specialists")
                .specialty("Orthopedics")
                .taxId("45-6789012")
                .inNetwork(true)
                .providerType("SPECIALTY_CLINIC")
                .email("info@texasortho.com")
                .phone("555-1004")
                .address("400 Bone & Joint Ave")
                .city("Houston")
                .state("TX")
                .zipCode("77002")
                .isActive(true)
                .credentialingStatus("ACTIVE")
                .fraudRiskScore(0.08)
                .build(),
            Provider.builder()
                .npi("5678901234")
                .name("Desert Heart Center")
                .specialty("Cardiology")
                .taxId("56-7890123")
                .inNetwork(false)
                .providerType("SPECIALTY_CLINIC")
                .email("heart@desertcardio.com")
                .phone("555-1005")
                .address("500 Heart Health Pkwy")
                .city("Phoenix")
                .state("AZ")
                .zipCode("85002")
                .isActive(true)
                .credentialingStatus("ACTIVE")
                .fraudRiskScore(0.2)
                .build(),
            Provider.builder()
                .npi("6789012345")
                .name("Suspicious Medical Services")
                .specialty("General Practice")
                .taxId("67-8901234")
                .inNetwork(true)
                .providerType("CLINIC")
                .email("contact@susmed.com")
                .phone("555-1006")
                .address("666 Shady Lane")
                .city("Miami")
                .state("FL")
                .zipCode("33101")
                .isActive(true)
                .credentialingStatus("UNDER_REVIEW")
                .fraudRiskScore(0.85)
                .build()
        );

        return providerRepository.persist(providers)
            .map(v -> providers)
            .onItem().invoke(p -> LOG.infof("Seeded %d providers", p.size()));
    }

    private Uni<Void> seedClaims(List<Patient> patients, List<Provider> providers) {
        List<Claim> claims = List.of(
            createClaim(patients.get(0), providers.get(0), ClaimType.MEDICAL,
                "500.00", ClaimStatus.APPROVED, "E11.9", "99213"),
            createClaim(patients.get(0), providers.get(1), ClaimType.MEDICAL,
                "150.00", ClaimStatus.SUBMITTED, "J06.9", "99212"),
            createClaim(patients.get(1), providers.get(2), ClaimType.MEDICAL,
                "1200.00", ClaimStatus.PENDING_ADJUDICATION, "M54.5", "72148"),
            createClaim(patients.get(1), providers.get(3), ClaimType.MEDICAL,
                "3500.00", ClaimStatus.PENDING_REVIEW, "S82.001A", "27759"),
            createClaim(patients.get(2), providers.get(4), ClaimType.MEDICAL,
                "8000.00", ClaimStatus.APPROVED, "I25.10", "93458"),
            createClaim(patients.get(2), providers.get(0), ClaimType.MEDICAL,
                "250.00", ClaimStatus.DENIED, "R51", "99214"),
            createClaim(patients.get(3), providers.get(1), ClaimType.MEDICAL,
                "175.00", ClaimStatus.VALIDATING, "N39.0", "81003"),
            createClaim(patients.get(3), providers.get(5), ClaimType.MEDICAL,
                "15000.00", ClaimStatus.FLAGGED_FRAUD, "M79.3", "99215"),
            createClaim(patients.get(4), providers.get(2), ClaimType.MEDICAL,
                "2200.00", ClaimStatus.APPROVED, "G43.909", "70553"),
            createClaim(patients.get(4), providers.get(3), ClaimType.MEDICAL,
                "4500.00", ClaimStatus.SUBMITTED, "M17.11", "27447")
        );

        return claimRepository.persist(claims)
            .replaceWithVoid()
            .onItem().invoke(() -> LOG.infof("Seeded %d claims", claims.size()));
    }

    private Claim createClaim(Patient patient, Provider provider, ClaimType type,
                               String amount, ClaimStatus status, String diagnosis, String procedure) {
        Claim claim = Claim.builder()
            .type(type)
            .amount(new BigDecimal(amount))
            .serviceDate(LocalDate.now().minusDays(random.nextInt(30)))
            .patientId(patient.getId())
            .providerId(provider.getId())
            .diagnosisCodes(diagnosis)
            .procedureCodes(procedure)
            .status(status)
            .build();

        if (status == ClaimStatus.APPROVED) {
            claim.setAllowedAmount(claim.getAmount().multiply(BigDecimal.valueOf(0.8)));
            claim.setReviewedBy("system");
            claim.setReviewedAt(java.time.LocalDateTime.now().minusDays(random.nextInt(7)));
        } else if (status == ClaimStatus.DENIED) {
            claim.setDenialReason("Service not covered under plan");
            claim.setReviewedBy("system");
            claim.setReviewedAt(java.time.LocalDateTime.now().minusDays(random.nextInt(7)));
        } else if (status == ClaimStatus.FLAGGED_FRAUD) {
            claim.setFraudScore(0.85);
            claim.setFraudReasons("Unusual billing pattern detected;Provider under review");
        }

        return claim;
    }
}
