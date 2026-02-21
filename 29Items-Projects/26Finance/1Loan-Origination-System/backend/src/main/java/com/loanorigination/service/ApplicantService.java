package com.loanorigination.service;

import com.loanorigination.model.Applicant;
import com.loanorigination.repository.ApplicantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ApplicantService {

    private final ApplicantRepository applicantRepository;

    @Transactional
    public Applicant createApplicant(Applicant applicant) {
        log.info("Creating applicant: {} {}", applicant.getFirstName(), applicant.getLastName());

        if (applicantRepository.existsByEmail(applicant.getEmail())) {
            throw new BusinessRuleException(
                    "An applicant with email " + applicant.getEmail() + " already exists");
        }

        if (applicant.getSsn() != null && applicantRepository.existsBySsn(applicant.getSsn())) {
            throw new BusinessRuleException("An applicant with this SSN already exists");
        }

        Applicant saved = applicantRepository.save(applicant);
        log.info("Applicant created with id: {}", saved.getId());
        return saved;
    }

    public Applicant getApplicantById(Long id) {
        return applicantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Applicant not found: " + id));
    }

    public List<Applicant> getAllApplicants() {
        return applicantRepository.findAll();
    }

    @Transactional
    public Applicant updateApplicant(Long id, Applicant updates) {
        Applicant existing = getApplicantById(id);

        existing.setFirstName(updates.getFirstName());
        existing.setLastName(updates.getLastName());
        existing.setPhone(updates.getPhone());
        existing.setDateOfBirth(updates.getDateOfBirth());
        existing.setAnnualIncome(updates.getAnnualIncome());
        existing.setEmploymentStatus(updates.getEmploymentStatus());
        existing.setEmployerName(updates.getEmployerName());
        existing.setYearsEmployed(updates.getYearsEmployed());

        return applicantRepository.save(existing);
    }

    public static class ResourceNotFoundException extends RuntimeException {
        public ResourceNotFoundException(String message) {
            super(message);
        }
    }

    public static class BusinessRuleException extends RuntimeException {
        public BusinessRuleException(String message) {
            super(message);
        }
    }
}
