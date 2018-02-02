package com.ap;

import pl.allegro.finance.tradukisto.ValueConverters;
import pl.allegro.finance.tradukisto.internal.converters.IntegerToWordsConverter;

public class NumbersToEnglishWords {
    public static void main(String[] args) {
        ValueConverters converter = ValueConverters.ENGLISH_INTEGER;
        String valueAsWords = converter.asWords(12);
        String valueAsWords1 = converter.asWords(00);

        System.out.println(valueAsWords+":"+valueAsWords1 + " minutes");
    }
}
