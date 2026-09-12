package com.ehrplatform.fhir.provider;

import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException;
import java.util.function.Supplier;

/**
 * Translates the platform's protocol-neutral exceptions into HAPI server
 * exceptions, so the FHIR layer returns a proper {@code OperationOutcome} with
 * the correct HTTP status (404 / 422). Keeps the providers free of try/catch noise.
 */
final class ProviderSupport {

    private ProviderSupport() {
    }

    static <T> T translate(Supplier<T> action) {
        try {
            return action.get();
        } catch (com.ehrplatform.common.exception.ResourceNotFoundException e) {
            throw new ResourceNotFoundException(e.getMessage());
        } catch (com.ehrplatform.common.exception.ValidationException e) {
            throw new UnprocessableEntityException(e.getMessage());
        }
    }

    static void run(Runnable action) {
        translate(() -> {
            action.run();
            return null;
        });
    }
}
