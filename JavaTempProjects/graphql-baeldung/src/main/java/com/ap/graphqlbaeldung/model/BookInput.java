package com.ap.graphqlbaeldung.model;

public class BookInput {
    private String name;
    private int pageCount;
    private String authorId;

    public BookInput(String id, String name, int pageCount, String authorId) {
        this.name = name;
        this.pageCount = pageCount;
        this.authorId = authorId;
    }
}
