/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.ap.managedbeans;

import com.ap.cdibeans.Greetings;
import javax.inject.Named;
import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;

/**
 *
 * @author andrii
 */
// RequestScoped - it is javax.enterprise.context.RequestScoped;
@Named
@RequestScoped
public class WelcomeBean {

    private String name;
    private String message;

    @Inject // in java EE 7 beans.xml is not required(cdi is enabled by default), but is included it with wrong parameter bean-discovery-mode="annotated" - and spend 40 minutes, after this I changed "annotated" to "all" and it works
    Greetings greetings;

    public Greetings getGreetings() {
        return greetings;
    }

    public void setGreetings(Greetings greetings) {
        this.greetings = greetings;
    }
    
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getMessage() {
        System.out.println("abdddddd" + greetings);
        return name + message + greetings.greet("name");
    }

    public void setMessage(String message) {
        this.message = message;
    }

    /**
     * Creates a new instance of WelcomeBean
     */
    public WelcomeBean() {
    }
    
}
