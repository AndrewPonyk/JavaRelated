package com.example.trading.application.exception;

public final class InsufficientFundsException extends TradingException {
    public InsufficientFundsException(String message) {
        super(message);
    }
}
