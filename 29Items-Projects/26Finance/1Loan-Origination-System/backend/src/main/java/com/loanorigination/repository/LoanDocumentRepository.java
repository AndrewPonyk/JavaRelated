package com.loanorigination.repository;

import com.loanorigination.model.LoanDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for {@link com.loanorigination.model.LoanDocument}.
 *
 * NOTE: The document upload feature is planned but not yet implemented.
 * The LoanDocument entity and this repository are retained so that the Flyway migration
 * (V2__create_underwriting_tables.sql) can create the underlying table.
 * When document upload is implemented, add an S3/blob-storage integration here.
 */
@Repository
public interface LoanDocumentRepository extends JpaRepository<LoanDocument, Long> {

    List<LoanDocument> findByApplicationId(Long applicationId);

    List<LoanDocument> findByApplicationIdAndDocumentType(Long applicationId, String documentType);

    List<LoanDocument> findByProcessed(String processed);

    long countByApplicationId(Long applicationId);
}
