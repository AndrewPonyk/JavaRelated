package com.ap.demo.controller;

import io.swagger.annotations.ApiModelProperty;

import javax.validation.constraints.NotNull;

public class Customer {
    @NotNull(message = "First Name should not be null!!!!") // THIS is for SPRING
    @ApiModelProperty(required = true)  // THIS is for SWAGGER!
    public String firstName;
    public String lastName;

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

}
