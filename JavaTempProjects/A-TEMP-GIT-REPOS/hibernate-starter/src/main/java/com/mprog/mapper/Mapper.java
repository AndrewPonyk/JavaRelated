package com.mprog.mapper;

public interface Mapper<F, T> {

    T mapFrom(F object);
}
