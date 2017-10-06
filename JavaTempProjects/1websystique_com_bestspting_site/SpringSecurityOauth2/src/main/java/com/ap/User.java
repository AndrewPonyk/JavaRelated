package com.ap;

import java.util.Map;

/**
 * Created by andrii on 05.10.17.
 */
public class User {
    private Object age;
    private Map<String, ?> id;
    private Object salary;
    private String name;

    public String getName() {
        return "hardcode";
    }

    public void setName(String name) {
        this.name = name;
    }

    public Object getAge() {
        return age;
    }

    public void setAge(Object age) {
        this.age = age;
    }

    public Object getSalary() {
        return salary;
    }

    public void setSalary(Object salary) {
        this.salary = salary;
    }

    public Map<String, ?> getId() {
        return id;
    }
}
