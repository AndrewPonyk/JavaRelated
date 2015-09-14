package com.ap.buildui.client.editors;

/**
 * Created by andrew on 09.06.15.
 */
public class Employee {
    private Long id;
    private String name;
    private String title;

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getTitle() {
        return title;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setTitle(String title) {
        this.title = title;
    }
}
