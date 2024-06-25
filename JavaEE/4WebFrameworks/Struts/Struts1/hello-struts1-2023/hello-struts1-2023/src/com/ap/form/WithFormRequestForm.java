package com.ap.form;

import org.apache.struts.action.ActionForm;

public class WithFormRequestForm extends ActionForm {
    private String message;

    //generate getters and setters
    public String getMessage() {
        return message;
    }
    public void setMessage(String message) {
        this.message = message;
    }
}
