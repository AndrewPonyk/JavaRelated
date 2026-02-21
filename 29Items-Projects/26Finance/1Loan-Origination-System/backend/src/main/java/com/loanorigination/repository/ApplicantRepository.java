package com.loanorigination.repository;

import com.loanorigination.model.Applicant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ApplicantRepository extends JpaRepository<Applicant, Long> {

    Optional<Applicant> findByEmail(String email);

    Optional<Applicant> findBySsn(String ssn);

    boolean existsByEmail(String email);

    boolean existsBySsn(String ssn);
}
