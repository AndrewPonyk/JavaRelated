package com.loanorigination.repository;

import com.loanorigination.model.UnderwritingDecision;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UnderwritingDecisionRepository extends JpaRepository<UnderwritingDecision, Long> {

    Optional<UnderwritingDecision> findByApplicationId(Long applicationId);

    List<UnderwritingDecision> findByDecision(UnderwritingDecision.Decision decision);

    @Query("SELECT ud FROM UnderwritingDecision ud WHERE ud.automated = true AND ud.decisionDate >= :since")
    List<UnderwritingDecision> findAutomatedDecisionsSince(LocalDateTime since);

    @Query("SELECT COUNT(ud) FROM UnderwritingDecision ud WHERE ud.decision = :decision AND ud.decisionDate >= :since")
    Long countByDecisionSince(UnderwritingDecision.Decision decision, LocalDateTime since);
}
