package com.ap.form;

import com.ap.action.HelloWorldAction;
import org.apache.struts.action.ActionForm;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class HelloWorldForm extends ActionForm {
    String greeting;
    String outMessage;
    String action;



    private List<HelloWorldAction.MyEntry> akaList;
    private Integer akaListSize;

    public HelloWorldForm() {
        if(akaList == null){
            akaListSize = 0;
            akaList = new ArrayList<>();
        }

        if(this.akaListSize > this.akaList.size()){
            int diff = this.akaListSize - this.akaList.size();
            for (int i = 0; i < diff; i++) {
                akaList.add(new HelloWorldAction.MyEntry());
            }
        }
    }

    public String getGreeting() {
        return greeting;
    }

    public void setGreeting(String greeting) {
        this.greeting = greeting;
    }

    public String getOutMessage() {
        return outMessage;
    }
    public void setOutMessage(String outMessage) {
        this.outMessage = outMessage;
    }
    public String getAction() {
        return action;
    }
    public void setAction(String action) {
        this.action = action;
    }

    public List<HelloWorldAction.MyEntry> getAkaList() {
        if(this.akaListSize > this.akaList.size()){
            int diff = this.akaListSize - this.akaList.size();
            for (int i = 0; i < diff; i++) {
                akaList.add(new HelloWorldAction.MyEntry());
            }
        }
        return akaList;
    }

    public void setAkaList(List<HelloWorldAction.MyEntry> akaList) {
       this.akaList = akaList;
    }


    public Integer getAkaListSize() {
        return akaListSize;
    }

    public void setAkaListSize(Integer akaListSize) {
        this.akaListSize = akaListSize;
    }
}
