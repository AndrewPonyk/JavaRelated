/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.ap.cdibeans;

import javax.enterprise.context.Dependent;
import javax.faces.bean.RequestScoped;
import javax.inject.Named;

/**
 *
 * @author andrii
 */

public class HelloGreetingsImpl implements Greetings{

    @Override
    public String greet(String name) {
       return "Hello, " + name + " (from CDI)";
    }
    
}
