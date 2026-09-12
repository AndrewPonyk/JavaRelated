package com.ehrplatform.fhir.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.ehrplatform.common.dto.ApiError;
import com.ehrplatform.common.exception.ResourceNotFoundException;
import com.ehrplatform.common.exception.ValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void notFoundMapsTo404WithCode() {
        ResponseEntity<ApiError> response =
                handler.handleNotFound(new ResourceNotFoundException("Patient", "x"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("RESOURCE_NOT_FOUND");
    }

    @Test
    void validationMapsTo422() {
        ResponseEntity<ApiError> response =
                handler.handleValidation(new ValidationException("bad"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody().code()).isEqualTo("VALIDATION_FAILED");
    }

    @Test
    void unexpectedMapsTo500WithoutLeakingDetail() {
        ResponseEntity<ApiError> response =
                handler.handleUnexpected(new RuntimeException("sensitive internal detail"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().code()).isEqualTo("INTERNAL_ERROR");
        // The safe message must not leak the internal exception text.
        assertThat(response.getBody().message()).doesNotContain("sensitive");
    }
}
