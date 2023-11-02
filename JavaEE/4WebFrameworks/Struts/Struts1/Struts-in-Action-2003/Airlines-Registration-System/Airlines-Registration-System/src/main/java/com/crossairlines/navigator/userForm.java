package com.crossairlines.navigator;

import org.apache.struts.action.ActionForm;


public class userForm extends ActionForm {

    private String action;
    private int id;


    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }


}