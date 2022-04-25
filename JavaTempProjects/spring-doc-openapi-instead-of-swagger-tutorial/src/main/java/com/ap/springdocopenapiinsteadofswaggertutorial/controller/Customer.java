package com.ap.springdocopenapiinsteadofswaggertutorial.controller;


import io.swagger.v3.oas.annotations.media.Schema;

import javax.validation.constraints.NotNull;

public class Customer {
    @NotNull(message = "First Name should not be null!!!!") // THIS is for SPRING
    //Swagger is like interface (springfox and newest spring-doc are implementations)
    @Schema(required = true) // THIS IS INSTED OF springfox @ApiModelProperty
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
