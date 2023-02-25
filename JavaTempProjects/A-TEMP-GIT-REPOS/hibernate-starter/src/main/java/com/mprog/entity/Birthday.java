package com.mprog.entity;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public record Birthday(LocalDate birthday) {

    public long getAge() {
        return ChronoUnit.YEARS.between(birthday, LocalDate.now());
    }
}
