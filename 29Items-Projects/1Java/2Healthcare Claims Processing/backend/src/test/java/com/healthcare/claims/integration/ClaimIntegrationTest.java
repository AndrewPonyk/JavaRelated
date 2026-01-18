package com.healthcare.claims.integration;

import com.healthcare.claims.api.dto.ClaimRequestDTO;
import com.healthcare.claims.api.dto.ClaimResponseDTO;
import com.healthcare.claims.api.dto.PatientRequestDTO;
import com.healthcare.claims.api.dto.PatientResponseDTO;
import com.healthcare.claims.api.dto.ProviderRequestDTO;
import com.healthcare.claims.api.dto.ProviderResponseDTO;
import com.healthcare.claims.domain.model.ClaimType;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Integration tests for the Claims Processing API.
 * Uses TestContainers for PostgreSQL, Kafka, Elasticsearch, and Redis.
 */
@QuarkusTest
@QuarkusTestResource(TestContainersResource.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestSecurity(user = "testuser", roles = {"admin", "claims-submitter", "claims-viewer", "claims-processor",
    "patient-admin", "patient-viewer", "provider-admin", "provider-viewer", "fraud-reviewer"})
class ClaimIntegrationTest {

    private static UUID testPatientId;
    private static UUID testProviderId;
    private static UUID createdClaimId;
    private static String createdClaimNumber;

    @Test
    @Order(1)
    @DisplayName("Should create a test patient")
    void shouldCreateTestPatient() {
        PatientRequestDTO request = PatientRequestDTO.builder()
            .memberId("INT-MEM-001")
            .firstName("Integration")
            .lastName("TestPatient")
            .dateOfBirth(LocalDate.of(1990, 5, 15))
            .policyNumber("INT-POL-001")
            .groupNumber("INT-GRP")
            .policyStartDate(LocalDate.now().minusYears(1))
            .policyEndDate(LocalDate.now().plusYears(1))
            .email("integration@test.com")
            .phone("555-INT1")
            .isActive(true)
            .build();

        PatientResponseDTO response = given()
            .contentType(ContentType.JSON)
            .body(request)
            .when()
            .post("/api/v1/patients")
            .then()
            .statusCode(201)
            .body("memberId", equalTo("INT-MEM-001"))
            .body("fullName", equalTo("Integration TestPatient"))
            .body("policyActive", equalTo(true))
            .extract()
            .as(PatientResponseDTO.class);

        testPatientId = response.getId();
        assertThat(testPatientId).isNotNull();
    }

    @Test
    @Order(2)
    @DisplayName("Should create a test provider")
    void shouldCreateTestProvider() {
        ProviderRequestDTO request = ProviderRequestDTO.builder()
            .npi("9999999901")
            .name("Integration Test Medical Center")
            .specialty("General Medicine")
            .taxId("99-9999901")
            .inNetwork(true)
            .providerType("HOSPITAL")
            .email("integration@hospital.com")
            .phone("555-INT2")
            .isActive(true)
            .credentialingStatus("ACTIVE")
            .build();

        ProviderResponseDTO response = given()
            .contentType(ContentType.JSON)
            .body(request)
            .when()
            .post("/api/v1/providers")
            .then()
            .statusCode(201)
            .body("npi", equalTo("9999999901"))
            .body("eligibleForClaims", equalTo(true))
            .extract()
            .as(ProviderResponseDTO.class);

        testProviderId = response.getId();
        assertThat(testProviderId).isNotNull();
    }

    @Test
    @Order(3)
    @DisplayName("Should submit a new claim via REST API")
    void shouldSubmitNewClaim() {
        Assumptions.assumeTrue(testPatientId != null, "Test patient must be created first");
        Assumptions.assumeTrue(testProviderId != null, "Test provider must be created first");

        ClaimRequestDTO request = ClaimRequestDTO.builder()
            .type(ClaimType.MEDICAL)
            .amount(new BigDecimal("350.00"))
            .serviceDate(LocalDate.now().minusDays(3))
            .patientId(testPatientId)
            .providerId(testProviderId)
            .diagnosisCodes("J06.9")
            .procedureCodes("99213")
            .notes("Integration test claim")
            .build();

        ClaimResponseDTO response = given()
            .contentType(ContentType.JSON)
            .body(request)
            .when()
            .post("/api/v1/claims")
            .then()
            .statusCode(201)
            .body("claimNumber", notNullValue())
            .body("status", equalTo("SUBMITTED"))
            .body("amount", equalTo(350.00f))
            .body("patientId", equalTo(testPatientId.toString()))
            .body("providerId", equalTo(testProviderId.toString()))
            .extract()
            .as(ClaimResponseDTO.class);

        createdClaimId = response.getId();
        createdClaimNumber = response.getClaimNumber();
        assertThat(createdClaimId).isNotNull();
        assertThat(createdClaimNumber).startsWith("CLM-");
    }

    @Test
    @Order(4)
    @DisplayName("Should retrieve claim by ID")
    void shouldRetrieveClaimById() {
        Assumptions.assumeTrue(createdClaimId != null, "Claim must be created first");

        given()
            .when()
            .get("/api/v1/claims/{id}", createdClaimId)
            .then()
            .statusCode(200)
            .body("id", equalTo(createdClaimId.toString()))
            .body("claimNumber", equalTo(createdClaimNumber))
            .body("status", notNullValue());
    }

    @Test
    @Order(5)
    @DisplayName("Should retrieve claim by claim number")
    void shouldRetrieveClaimByNumber() {
        Assumptions.assumeTrue(createdClaimNumber != null, "Claim must be created first");

        given()
            .when()
            .get("/api/v1/claims/number/{claimNumber}", createdClaimNumber)
            .then()
            .statusCode(200)
            .body("claimNumber", equalTo(createdClaimNumber));
    }

    @Test
    @Order(6)
    @DisplayName("Should list claims with pagination")
    void shouldListClaimsWithPagination() {
        given()
            .queryParam("page", 0)
            .queryParam("size", 10)
            .when()
            .get("/api/v1/claims")
            .then()
            .statusCode(200)
            .body("$", hasSize(lessThanOrEqualTo(10)));
    }

    @Test
    @Order(7)
    @DisplayName("Should filter claims by status")
    void shouldFilterClaimsByStatus() {
        given()
            .queryParam("status", "SUBMITTED")
            .when()
            .get("/api/v1/claims")
            .then()
            .statusCode(200);
    }

    @Test
    @Order(8)
    @DisplayName("Should get claims for patient")
    void shouldGetClaimsForPatient() {
        Assumptions.assumeTrue(testPatientId != null, "Test patient must be created first");

        given()
            .when()
            .get("/api/v1/claims/patient/{patientId}", testPatientId)
            .then()
            .statusCode(200)
            .body("$", not(empty()));
    }

    @Test
    @Order(9)
    @DisplayName("Should process claim through adjudication")
    void shouldProcessClaimThroughAdjudication() {
        Assumptions.assumeTrue(createdClaimId != null, "Claim must be created first");

        given()
            .contentType(ContentType.JSON)
            .when()
            .post("/api/v1/claims/{id}/process", createdClaimId)
            .then()
            .statusCode(200)
            .body("id", equalTo(createdClaimId.toString()));
    }

    @Test
    @Order(10)
    @DisplayName("Should return 404 for non-existent claim")
    void shouldReturn404ForNonExistentClaim() {
        given()
            .when()
            .get("/api/v1/claims/{id}", UUID.randomUUID())
            .then()
            .statusCode(404)
            .body("message", containsString("not found"));
    }

    @Test
    @Order(11)
    @DisplayName("Should validate claim input - missing amount")
    void shouldValidateClaimInputMissingAmount() {
        ClaimRequestDTO invalidRequest = ClaimRequestDTO.builder()
            .type(ClaimType.MEDICAL)
            .serviceDate(LocalDate.now().minusDays(5))
            .patientId(testPatientId)
            .providerId(testProviderId)
            .build();

        given()
            .contentType(ContentType.JSON)
            .body(invalidRequest)
            .when()
            .post("/api/v1/claims")
            .then()
            .statusCode(400);
    }

    @Test
    @Order(12)
    @DisplayName("Should reject claim with future service date")
    void shouldRejectClaimWithFutureServiceDate() {
        Assumptions.assumeTrue(testPatientId != null && testProviderId != null);

        ClaimRequestDTO invalidRequest = ClaimRequestDTO.builder()
            .type(ClaimType.MEDICAL)
            .amount(new BigDecimal("100.00"))
            .serviceDate(LocalDate.now().plusDays(10))
            .patientId(testPatientId)
            .providerId(testProviderId)
            .build();

        given()
            .contentType(ContentType.JSON)
            .body(invalidRequest)
            .when()
            .post("/api/v1/claims")
            .then()
            .statusCode(400);
    }

    @Test
    @Order(20)
    @DisplayName("Health check should return UP")
    void healthCheckShouldReturnUp() {
        given()
            .when()
            .get("/api/v1/health")
            .then()
            .statusCode(200)
            .body("status", equalTo("UP"));
    }

    @Test
    @Order(21)
    @DisplayName("Readiness check should return UP")
    void readinessCheckShouldReturnUp() {
        given()
            .when()
            .get("/api/v1/health/ready")
            .then()
            .statusCode(200)
            .body("status", equalTo("UP"));
    }

    @Test
    @Order(22)
    @DisplayName("Detailed health check should return component status")
    void detailedHealthCheckShouldReturnComponentStatus() {
        given()
            .when()
            .get("/api/v1/health/detailed")
            .then()
            .statusCode(200)
            .body("status", anyOf(equalTo("UP"), equalTo("DEGRADED")))
            .body("application", notNullValue())
            .body("components", notNullValue());
    }

    @Test
    @Order(30)
    @DisplayName("Should search patients by name")
    void shouldSearchPatientsByName() {
        given()
            .queryParam("name", "Integration")
            .when()
            .get("/api/v1/patients/search")
            .then()
            .statusCode(200)
            .body("$", not(empty()));
    }

    @Test
    @Order(31)
    @DisplayName("Should get active patients")
    void shouldGetActivePatients() {
        given()
            .when()
            .get("/api/v1/patients/active")
            .then()
            .statusCode(200);
    }

    @Test
    @Order(32)
    @DisplayName("Should get in-network providers")
    void shouldGetInNetworkProviders() {
        given()
            .when()
            .get("/api/v1/providers/in-network")
            .then()
            .statusCode(200);
    }

    @Test
    @Order(33)
    @DisplayName("Should search providers by name")
    void shouldSearchProvidersByName() {
        given()
            .queryParam("name", "Integration")
            .when()
            .get("/api/v1/providers/search")
            .then()
            .statusCode(200)
            .body("$", not(empty()));
    }
}
