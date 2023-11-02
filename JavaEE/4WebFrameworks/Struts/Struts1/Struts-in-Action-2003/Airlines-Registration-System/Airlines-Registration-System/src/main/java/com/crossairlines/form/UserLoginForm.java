package com.crossairlines.form;

import javax.servlet.http.HttpServletRequest;

import org.apache.struts.action.ActionErrors;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.action.ActionMessage;

public class UserLoginForm extends ActionForm{

    private static final long serialVersionUID = -5458492377061659764L;
    private String password;
    private String email;

    public String getPassword() {
        return password;
    }
    public void setPassword(String password) {
        this.password = password;
    }
    public String getEmail() {
        return email;
    }
    public void setEmail(String email) {
        this.email = email;
    }
    @Override
    public String toString() {
        return "UserForm [password=" + password + ", email=" + email + "]";
    }

    @Override
    public ActionErrors validate(ActionMapping mapping, HttpServletRequest request) {
        ActionErrors ae = new ActionErrors();
        String name = request.getParameter("email");
        String pass = request.getParameter("password");
        System.out.println(name+", "+pass);
        if(name == null || pass == null ) {
            return ae;
        }
        if (name.trim().equals(""))
            ae.add("name", new ActionMessage("error.name.required"));
        if (pass.trim().equals(""))
            ae.add("pass", new ActionMessage("error.pass.required"));
        return ae;
    }
}