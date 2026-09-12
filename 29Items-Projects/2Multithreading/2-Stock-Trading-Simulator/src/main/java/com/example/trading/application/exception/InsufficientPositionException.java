package com.example.trading.application.exception;

public final class InsufficientPositionException extends TradingException {
    public InsufficientPositionException(String message) {
        super(message);
    }
}
