package com.ap.model;

import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class Course {
    private String name;
    private List<String> codes;
    private Map<String, Student> enrolledStudents = new HashMap<String, Student>();

    @Override
    public String toString() {
        return "Course{" +
                "name='" + name + '\'' +
                ", codes=" + codes +
                ", enrolledStudens=" + enrolledStudents +
                '}';
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<String> getCodes() {
        return codes;
    }

    public void setCodes(List<String> codes) {
        this.codes = codes;
    }

    public Map<String, Student> getEnrolledStudents() {
        return enrolledStudents;
    }

    public void setEnrolledStudents(Map<String, Student> enrolledStudens) {
        this.enrolledStudents = enrolledStudens;
    }
}
