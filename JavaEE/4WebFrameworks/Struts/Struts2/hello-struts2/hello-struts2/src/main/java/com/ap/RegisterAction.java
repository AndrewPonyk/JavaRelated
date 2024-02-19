package com.ap;

import com.opensymphony.xwork2.ActionSupport;

public class RegisterAction extends ActionSupport {

  private String username;
  private String email;

  public String execute() {
    // save form data to database 
    username+= "Preprocessed in java123";
    return SUCCESS;
  }

public String getUsername() {
    return username;
}

public void setUsername(String username) {
    this.username = username;
}

public String getEmail() {
    return email;
}

public void setEmail(String email) {
    this.email = email;
}

  
}