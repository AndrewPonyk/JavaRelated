package com.loanorigination.service;

import com.loanorigination.dto.DocumentSearchResultDto;
import com.loanorigination.model.LoanDocument;
import com.loanorigination.model.LoanDocumentIndex;
import com.loanorigination.repository.LoanDocumentSearchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.Criteria;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentSearchService {

    private final LoanDocumentSearchRepository searchRepository;
    private final ElasticsearchOperations elasticsearchOperations;

    /**
     * Index a document after upload or OCR processing.
     */
    public void indexDocument(LoanDocument document) {
        LoanDocumentIndex index = LoanDocumentIndex.builder()
                .id(String.valueOf(document.getId()))
                .documentId(document.getId())
                .applicationId(document.getApplicationId())
                .documentType(document.getDocumentType())
                .documentName(document.getDocumentName())
                .mimeType(document.getMimeType())
                .uploadedAt(document.getUploadedAt())
                .ocrText(document.getOcrText())
                .build();

        searchRepository.save(index);
        log.info("Indexed document id={} in Elasticsearch", document.getId());
    }

    /**
     * Remove a document from the index on delete.
     */
    public void removeFromIndex(Long documentId) {
        searchRepository.deleteById(String.valueOf(documentId));
        log.info("Removed document id={} from Elasticsearch index", documentId);
    }

    /**
     * Full-text search across documentName, documentType, and ocrText.
     */
    public List<DocumentSearchResultDto> search(String query, Long applicationId) {
        Criteria criteria = new Criteria("documentName").matches(query)
                .or("ocrText").matches(query)
                .or("documentType").matches(query);

        if (applicationId != null) {
            criteria = criteria.and("applicationId").is(applicationId);
        }

        CriteriaQuery searchQuery = new CriteriaQuery(criteria);

        SearchHits<LoanDocumentIndex> hits = elasticsearchOperations.search(
                searchQuery, LoanDocumentIndex.class);

        return hits.getSearchHits().stream()
                .map(this::mapToSearchResult)
                .toList();
    }

    private DocumentSearchResultDto mapToSearchResult(SearchHit<LoanDocumentIndex> hit) {
        LoanDocumentIndex doc = hit.getContent();
        String snippet = doc.getOcrText() != null
                ? doc.getOcrText().substring(0, Math.min(200, doc.getOcrText().length()))
                : "";

        return DocumentSearchResultDto.builder()
                .documentId(doc.getDocumentId())
                .applicationId(doc.getApplicationId())
                .documentType(doc.getDocumentType())
                .documentName(doc.getDocumentName())
                .mimeType(doc.getMimeType())
                .uploadedAt(doc.getUploadedAt())
                .ocrTextSnippet(snippet)
                .score(hit.getScore())
                .build();
    }
}
