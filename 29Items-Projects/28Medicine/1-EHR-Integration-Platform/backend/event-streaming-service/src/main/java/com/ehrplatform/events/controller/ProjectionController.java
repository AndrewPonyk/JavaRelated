package com.ehrplatform.events.controller;

import com.ehrplatform.events.model.PatientProjection;
import com.ehrplatform.events.service.ProjectionStore;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read API over the CQRS projection built from the event stream.
 */
@RestController
@RequestMapping("/api/v1/projections")
public class ProjectionController {

    private final ProjectionStore projectionStore;

    public ProjectionController(ProjectionStore projectionStore) {
        this.projectionStore = projectionStore;
    }

    @GetMapping("/patients/{id}")
    public ResponseEntity<PatientProjection> patient(@PathVariable String id) {
        return projectionStore.findPatient(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/stats")
    public Stats stats() {
        return new Stats(projectionStore.distinctPatients(), projectionStore.totalEventsApplied());
    }

    public record Stats(int distinctPatients, long totalEventsApplied) {
    }
}
