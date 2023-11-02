package com.ap.form;

import org.apache.struts.action.ActionForm;

public class HelloWorldForm extends ActionForm {
    String greeting;

    public String getGreeting() {
        return greeting;
    }

    public void setGreeting(String greeting) {
        this.greeting = greeting;
    }}
