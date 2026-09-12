package com.stringalgorithms.algorithms;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public final class SuffixArray {
    private final String text;
    private final List<Integer> suffixIndexes;

    public SuffixArray(String text) {
        this.text = Objects.requireNonNull(text, "text must not be null");
        this.suffixIndexes = IntStream.range(0, text.length())
                .boxed()
                .sorted(Comparator.comparing(text::substring))
                .collect(Collectors.toList());
    }

    public List<Integer> indexes() {
        return suffixIndexes;
    }

    public List<Integer> search(String pattern) {
        Objects.requireNonNull(pattern, "pattern must not be null");
        if (pattern.isEmpty()) {
            throw new IllegalArgumentException("pattern must not be empty");
        }

        List<Integer> matches = new ArrayList<>();
        for (int suffixIndex : suffixIndexes) {
            if (text.startsWith(pattern, suffixIndex)) {
                matches.add(suffixIndex);
            }
        }
        matches.sort(Integer::compareTo);
        return matches;
    }
}
