package com.ap.dto;

import org.springframework.hateoas.ResourceSupport;

public class Greeting extends ResourceSupport {
    private String content;

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}