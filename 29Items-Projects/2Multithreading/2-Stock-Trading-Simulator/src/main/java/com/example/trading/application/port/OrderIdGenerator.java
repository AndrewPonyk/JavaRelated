package com.example.trading.application.port;

@FunctionalInterface
public interface OrderIdGenerator {
    long nextId();
}
