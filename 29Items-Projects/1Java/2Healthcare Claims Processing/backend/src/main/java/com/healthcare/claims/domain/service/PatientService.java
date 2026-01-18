package com.healthcare.claims.domain.service;

import com.healthcare.claims.domain.model.Patient;
import com.healthcare.claims.domain.repository.PatientRepository;
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
 * Service for patient management operations.
 */
@ApplicationScoped
public class PatientService {

    private static final Logger LOG = Logger.getLogger(PatientService.class);

    @Inject
    PatientRepository patientRepository;

    /**
     * Creates a new patient.
     */
    @WithTransaction
    public Uni<Patient> createPatient(@Valid Patient patient) {
        LOG.infof("Creating patient with member ID: %s", patient.getMemberId());

        return patientRepository.existsByMemberId(patient.getMemberId())
            .flatMap(exists -> {
                if (exists) {
                    return Uni.createFrom().failure(
                        new IllegalArgumentException("Patient with member ID already exists: " + patient.getMemberId())
                    );
                }
                if (patient.getIsActive() == null) {
                    patient.setIsActive(true);
                }
                return patientRepository.persist(patient);
            })
            .onItem().invoke(p -> LOG.infof("Patient created: %s", p.getId()));
    }

    /**
     * Retrieves a patient by ID.
     */
    public Uni<Patient> getPatientById(UUID id) {
        return patientRepository.findById(id);
    }

    /**
     * Retrieves a patient by member ID.
     */
    public Uni<Patient> getPatientByMemberId(String memberId) {
        return patientRepository.findByMemberId(memberId);
    }

    /**
     * Retrieves a patient by policy number.
     */
    public Uni<Patient> getPatientByPolicyNumber(String policyNumber) {
        return patientRepository.findByPolicyNumber(policyNumber);
    }

    /**
     * Retrieves all patients with pagination.
     */
    public Uni<List<Patient>> getAllPatients(int page, int size) {
        return patientRepository.findAll(Sort.descending("createdAt"))
            .page(Page.of(page, size))
            .list();
    }

    /**
     * Retrieves active patients.
     */
    public Uni<List<Patient>> getActivePatients() {
        return patientRepository.findActivePatients();
    }

    /**
     * Searches patients by name.
     */
    public Uni<List<Patient>> searchPatientsByName(String name) {
        String searchPattern = "%" + name.toLowerCase() + "%";
        return patientRepository.list(
            "lower(firstName) like ?1 or lower(lastName) like ?1",
            searchPattern
        );
    }

    /**
     * Updates an existing patient.
     */
    @WithTransaction
    public Uni<Patient> updatePatient(UUID id, @Valid Patient updatedPatient) {
        LOG.infof("Updating patient: %s", id);

        return patientRepository.findById(id)
            .flatMap(existingPatient -> {
                if (existingPatient == null) {
                    return Uni.createFrom().failure(
                        new IllegalArgumentException("Patient not found: " + id)
                    );
                }

                // Check if member ID is being changed to one that already exists
                if (!existingPatient.getMemberId().equals(updatedPatient.getMemberId())) {
                    return patientRepository.existsByMemberId(updatedPatient.getMemberId())
                        .flatMap(exists -> {
                            if (exists) {
                                return Uni.createFrom().failure(
                                    new IllegalArgumentException("Member ID already in use: " + updatedPatient.getMemberId())
                                );
                            }
                            return updatePatientFields(existingPatient, updatedPatient);
                        });
                }

                return updatePatientFields(existingPatient, updatedPatient);
            })
            .onItem().invoke(p -> LOG.infof("Patient updated: %s", p.getId()));
    }

    /**
     * Deactivates a patient.
     */
    @WithTransaction
    public Uni<Patient> deactivatePatient(UUID id) {
        LOG.infof("Deactivating patient: %s", id);

        return patientRepository.findById(id)
            .flatMap(patient -> {
                if (patient == null) {
                    return Uni.createFrom().failure(
                        new IllegalArgumentException("Patient not found: " + id)
                    );
                }
                patient.setIsActive(false);
                return patientRepository.persist(patient);
            })
            .onItem().invoke(p -> LOG.infof("Patient deactivated: %s", p.getId()));
    }

    /**
     * Reactivates a patient.
     */
    @WithTransaction
    public Uni<Patient> reactivatePatient(UUID id) {
        LOG.infof("Reactivating patient: %s", id);

        return patientRepository.findById(id)
            .flatMap(patient -> {
                if (patient == null) {
                    return Uni.createFrom().failure(
                        new IllegalArgumentException("Patient not found: " + id)
                    );
                }
                patient.setIsActive(true);
                return patientRepository.persist(patient);
            })
            .onItem().invoke(p -> LOG.infof("Patient reactivated: %s", p.getId()));
    }

    /**
     * Deletes a patient (hard delete).
     */
    @WithTransaction
    public Uni<Boolean> deletePatient(UUID id) {
        LOG.infof("Deleting patient: %s", id);

        return patientRepository.deleteById(id)
            .onItem().invoke(deleted -> {
                if (deleted) {
                    LOG.infof("Patient deleted: %s", id);
                } else {
                    LOG.warnf("Patient not found for deletion: %s", id);
                }
            });
    }

    /**
     * Counts total patients.
     */
    public Uni<Long> countPatients() {
        return patientRepository.count();
    }

    /**
     * Counts active patients.
     */
    public Uni<Long> countActivePatients() {
        return patientRepository.count("isActive", true);
    }

    private Uni<Patient> updatePatientFields(Patient existing, Patient updated) {
        existing.setMemberId(updated.getMemberId());
        existing.setFirstName(updated.getFirstName());
        existing.setLastName(updated.getLastName());
        existing.setDateOfBirth(updated.getDateOfBirth());
        existing.setPolicyNumber(updated.getPolicyNumber());
        existing.setGroupNumber(updated.getGroupNumber());
        existing.setPolicyStartDate(updated.getPolicyStartDate());
        existing.setPolicyEndDate(updated.getPolicyEndDate());
        existing.setEmail(updated.getEmail());
        existing.setPhone(updated.getPhone());
        existing.setAddress(updated.getAddress());
        existing.setCity(updated.getCity());
        existing.setState(updated.getState());
        existing.setZipCode(updated.getZipCode());
        if (updated.getIsActive() != null) {
            existing.setIsActive(updated.getIsActive());
        }
        return patientRepository.persist(existing);
    }
}
