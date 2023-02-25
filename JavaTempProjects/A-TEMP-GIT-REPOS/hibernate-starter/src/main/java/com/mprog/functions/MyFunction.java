package com.mprog.functions;

@FunctionalInterface
public interface MyFunction<T, R> {

    R apply(T t);
}
