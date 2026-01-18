package com.bank.account.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when an account operation is invalid for the current state.
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidAccountStateException extends RuntimeException {

    public InvalidAccountStateException(String message) {
        super(message);
    }

    public InvalidAccountStateException(String message, Throwable cause) {
        super(message, cause);
    }
}
