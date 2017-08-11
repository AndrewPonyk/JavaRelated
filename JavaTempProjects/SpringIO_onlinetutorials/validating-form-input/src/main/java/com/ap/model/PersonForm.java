package com.ap.model;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

/**
 * Created by andrii on 09.08.17.
 */
public class PersonForm {
    @NotNull
    @Size(min = 2, max = 30)
    private String name;
    @NotNull
    @Min(16)
    private Integer age;

    @Override
    public String toString() {
        return "PersonForm{" +
                "age=" + age +
                ", name='" + name + '\'' +
                '}';
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getAge() {
        return age;
    }

    public void setAge(Integer age) {
        this.age = age;
    }
}
