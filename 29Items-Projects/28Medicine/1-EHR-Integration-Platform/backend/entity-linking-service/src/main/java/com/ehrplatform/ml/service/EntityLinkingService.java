package com.ehrplatform.ml.service;

import com.ehrplatform.ml.controller.StratificationDtos.LinkRequest;
import com.ehrplatform.ml.controller.StratificationDtos.LinkResponse;
import com.ehrplatform.ml.controller.StratificationDtos.StratificationRequest;
import com.ehrplatform.ml.controller.StratificationDtos.StratificationResponse;
import com.ehrplatform.ml.service.NoteCorpus.ScoredNote;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Risk stratification + entity linking over similar clinical notes.
 *
 * <p>Stratification is a cosine-similarity kNN regressor: embed the query note,
 * find the most similar notes in the {@link NoteCorpus}, and compute a
 * similarity-weighted average of their known risk weights. Entity linking scores
 * how likely two patients' notes describe the same/related case.
 */
@Service
public class EntityLinkingService {

    private static final Logger log = LoggerFactory.getLogger(EntityLinkingService.class);
    private static final double LINK_THRESHOLD = 0.5;

    private final TextVectorizer vectorizer;
    private final NoteCorpus corpus;

    public EntityLinkingService(TextVectorizer vectorizer, NoteCorpus corpus) {
        this.vectorizer = vectorizer;
        this.corpus = corpus;
    }

    public StratificationResponse stratify(StratificationRequest request) {
        Map<String, Double> query = vectorizer.vectorize(request.noteText());
        List<ScoredNote> neighbors = corpus.nearest(query, request.topK());
        log.info("Stratify patient {}: {} similar notes (corpus={})",
                request.patientId(), neighbors.size(), corpus.size());

        double score = weightedRisk(neighbors);
        List<String> cohort = neighbors.stream().map(n -> n.note().id()).toList();
        return new StratificationResponse(request.patientId(), round(score), toTier(score), cohort);
    }

    public LinkResponse link(LinkRequest request) {
        double confidence = CosineSimilarity.between(
                vectorizer.vectorize(request.leftNote()),
                vectorizer.vectorize(request.rightNote()));
        String status = confidence >= LINK_THRESHOLD ? "CANDIDATE" : "REJECTED";
        return new LinkResponse(request.leftPatientId(), request.rightPatientId(),
                round(confidence), status);
    }

    /** Similarity-weighted average of neighbor risk weights → score in [0,1]. */
    private double weightedRisk(List<ScoredNote> neighbors) {
        double weightedSum = 0.0;
        double similaritySum = 0.0;
        for (ScoredNote n : neighbors) {
            weightedSum += n.similarity() * n.note().riskWeight();
            similaritySum += n.similarity();
        }
        return similaritySum == 0.0 ? 0.0 : weightedSum / similaritySum;
    }

    private String toTier(double score) {
        if (score >= 0.66) {
            return "HIGH";
        }
        return score >= 0.33 ? "MODERATE" : "LOW";
    }

    private double round(double value) {
        return Math.round(value * 10_000.0) / 10_000.0;
    }
}
