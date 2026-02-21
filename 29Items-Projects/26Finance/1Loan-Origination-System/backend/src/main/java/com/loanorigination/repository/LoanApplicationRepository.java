package com.loanorigination.repository;

import com.loanorigination.model.LoanApplication;
import com.loanorigination.model.LoanApplication.ApplicationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

@Repository
public interface LoanApplicationRepository extends JpaRepository<LoanApplication, Long> {

    Optional<LoanApplication> findByApplicationId(String applicationId);

    List<LoanApplication> findByStatus(ApplicationStatus status);

    List<LoanApplication> findByApplicantId(Long applicantId);

    @Query("SELECT la FROM LoanApplication la WHERE la.status = :status AND la.createdAt >= :since")
    List<LoanApplication> findRecentApplicationsByStatus(ApplicationStatus status, LocalDateTime since);

    // Fixed: was using string literal 'APPROVED'; now uses a typed parameter to compare against the enum
    @Query("SELECT COUNT(la) FROM LoanApplication la WHERE la.status = :status AND la.decisionDate >= :since")
    Long countApprovedApplicationsSince(@Param("status") ApplicationStatus status, @Param("since") LocalDateTime since);

    @Query("SELECT la FROM LoanApplication la WHERE la.createdAt BETWEEN :startDate AND :endDate ORDER BY la.createdAt DESC")
    List<LoanApplication> findByDateRange(LocalDateTime startDate, LocalDateTime endDate);

    /**
     * Returns both timestamps so the average approval time can be computed in Java.
     * TIMESTAMPDIFF() was replaced because it is MySQL-specific and the project uses Oracle.
     * Each element of the returned array is [createdAt, decisionDate].
     */
    @Query("SELECT la.createdAt, la.decisionDate FROM LoanApplication la WHERE la.decisionDate IS NOT NULL")
    List<Object[]> findCreatedAndDecisionDates();

    /**
     * Computes average approval time in minutes from the two timestamp columns.
     * This is a default method so no native query is needed.
     */
    default OptionalDouble calculateAverageApprovalTimeMinutes() {
        List<Object[]> rows = findCreatedAndDecisionDates();
        return rows.stream()
                .mapToLong(row -> {
                    LocalDateTime created = (LocalDateTime) row[0];
                    LocalDateTime decided = (LocalDateTime) row[1];
                    return ChronoUnit.MINUTES.between(created, decided);
                })
                .average();
    }

    @Query("SELECT la.status, COUNT(la) FROM LoanApplication la GROUP BY la.status")
    List<Object[]> getApplicationStatusCounts();
}
