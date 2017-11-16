package com.ap.entity;


import com.ap.model.Student;

import java.util.List;
import java.util.Map;

public class CourseEntity {
    private String name;
    private List<String> codes;
    private Map<String, Student> enrolledStudents;

    private String stundenNamesFieldIsNotPresentInDto;

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

    public void setEnrolledStudents(Map<String, Student> enrolledStudents) {
        this.enrolledStudents = enrolledStudents;
    }

    public String getStundenNamesFieldIsNotPresentInDto() {
        return stundenNamesFieldIsNotPresentInDto;
    }

    public void setStundenNamesFieldIsNotPresentInDto(String stundenNamesFieldIsNotPresentInDto) {
        this.stundenNamesFieldIsNotPresentInDto = stundenNamesFieldIsNotPresentInDto;
    }

    @Override
    public String toString() {
        return "CourseEntity{" +
                "name='" + name + '\'' +
                ", codes=" + codes +
                ", enrolledStudents=" + enrolledStudents +
                ", stundenNamesFieldIsNotPresentInDto='" + stundenNamesFieldIsNotPresentInDto + '\'' +
                '}';
    }
}
