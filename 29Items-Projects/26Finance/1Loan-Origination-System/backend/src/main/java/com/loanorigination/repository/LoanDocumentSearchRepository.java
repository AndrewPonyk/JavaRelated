package com.loanorigination.repository;

import com.loanorigination.model.LoanDocumentIndex;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LoanDocumentSearchRepository extends ElasticsearchRepository<LoanDocumentIndex, String> {

    List<LoanDocumentIndex> findByApplicationId(Long applicationId);

    List<LoanDocumentIndex> findByDocumentType(String documentType);
}
