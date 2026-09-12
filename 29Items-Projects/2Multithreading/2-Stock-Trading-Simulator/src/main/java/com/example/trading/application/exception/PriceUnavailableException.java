package com.example.trading.application.exception;

public final class PriceUnavailableException extends TradingException {
    public PriceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
