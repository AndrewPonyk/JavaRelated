package com.ap.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Created by andrii on 07.08.17.
 */
public class Greeting {
    private final long id;
    @JsonIgnore
    private final String content;

    public Greeting(long id, String content) {
        this.id = id;
        this.content = content;
    }

    public long getId() {
        return id;
    }

    public String getContent() {
        return content;
    }
}
