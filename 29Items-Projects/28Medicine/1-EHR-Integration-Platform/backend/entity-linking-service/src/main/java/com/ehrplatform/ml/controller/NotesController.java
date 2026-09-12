package com.ehrplatform.ml.controller;

import com.ehrplatform.ml.service.NoteCorpus;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ingest clinical notes into the similarity corpus. In production this is fed
 * from encounter/observation events; exposed here as an API for direct ingestion
 * and testing.
 */
@RestController
@RequestMapping("/api/v1/notes")
public class NotesController {

    private final NoteCorpus corpus;

    public NotesController(NoteCorpus corpus) {
        this.corpus = corpus;
    }

    @PostMapping
    public ResponseEntity<Void> add(@RequestBody AddNoteRequest request) {
        corpus.add(request.noteId(), request.patientId(), request.text(),
                request.riskTier() == null ? "LOW" : request.riskTier());
        return ResponseEntity.accepted().build();
    }

    public record AddNoteRequest(
            @NotBlank String noteId,
            @NotBlank String patientId,
            @NotBlank String text,
            String riskTier) {
    }
}
