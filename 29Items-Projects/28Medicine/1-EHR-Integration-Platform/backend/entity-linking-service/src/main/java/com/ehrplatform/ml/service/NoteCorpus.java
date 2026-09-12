package com.ehrplatform.ml.service;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Component;

/**
 * In-memory corpus of clinical notes that powers similarity search.
 *
 * <p>Seeded at startup with a small synthetic, de-identified reference set across
 * risk tiers; notes from live encounters are appended as events arrive
 * the notes ingestion API). kNN over cosine similarity gives the
 * "free stratification using similar notes" capability without an external DB.
 * Scale note: back this with a real vector index (e.g. pgvector / OpenSearch
 * kNN) in production — the {@link #nearest} interface stays the same.
 */
@Component
public class NoteCorpus {

    private final TextVectorizer vectorizer;
    private final List<ClinicalNote> notes = new CopyOnWriteArrayList<>();

    public NoteCorpus(TextVectorizer vectorizer) {
        this.vectorizer = vectorizer;
    }

    @PostConstruct
    void seed() {
        add("seed-h1", "ref-h1", "severe chest pain shortness of breath elevated troponin acute myocardial infarction", "HIGH");
        add("seed-h2", "ref-h2", "sepsis hypotension lactic acidosis altered mental status intensive care", "HIGH");
        add("seed-h3", "ref-h3", "stroke facial droop slurred speech weakness urgent thrombolysis", "HIGH");
        add("seed-m1", "ref-m1", "hypertension elevated blood pressure medication adjustment follow up", "MODERATE");
        add("seed-m2", "ref-m2", "type two diabetes elevated glucose hemoglobin diet counseling", "MODERATE");
        add("seed-m3", "ref-m3", "asthma mild wheezing inhaler prescribed review in two weeks", "MODERATE");
        add("seed-l1", "ref-l1", "routine annual checkup healthy normal vital signs no complaints", "LOW");
        add("seed-l2", "ref-l2", "seasonal allergy mild congestion antihistamine advised", "LOW");
        add("seed-l3", "ref-l3", "follow up wellness visit stable no acute issues", "LOW");
    }

    public void add(String id, String patientId, String text, String riskTier) {
        notes.add(new ClinicalNote(id, patientId, text, riskTier, vectorizer.vectorize(text)));
    }

    public int size() {
        return notes.size();
    }

    /** Top-{@code k} notes most similar to {@code queryVector}, similarity desc. */
    public List<ScoredNote> nearest(java.util.Map<String, Double> queryVector, int k) {
        List<ScoredNote> scored = new ArrayList<>(notes.size());
        for (ClinicalNote note : notes) {
            double sim = CosineSimilarity.between(queryVector, note.vector());
            if (sim > 0.0) {
                scored.add(new ScoredNote(note, sim));
            }
        }
        scored.sort(Comparator.comparingDouble(ScoredNote::similarity).reversed());
        return scored.size() > k ? scored.subList(0, k) : scored;
    }

    /** A note paired with its similarity to a query. */
    public record ScoredNote(ClinicalNote note, double similarity) {
    }
}
