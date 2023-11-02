package com.crossairlines.form;


import javax.servlet.http.HttpServletRequest;

import org.apache.struts.action.ActionErrors;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.action.ActionMessage;

public class UserRegisterForm extends ActionForm{

    private static final long serialVersionUID = 45996344836395192L;

    private String conpassword;
    private String email;
    private String password;


    public void setConpassword(String conpassword) {
        this.conpassword = conpassword;
    }
    public String getPassword() {
        return password;
    }
    public void setPassword(String password) {
        this.password = password;
    }
    public String getConpassword() {
        return conpassword;
    }
    public void setConPass(String conpassword) {
        this.conpassword = conpassword;
    }
    public String getEmail() {
        return email;
    }
    public void setEmail(String email) {
        this.email = email;
    }
    @Override
    public ActionErrors validate(ActionMapping mapping, HttpServletRequest request) {
        ActionErrors ae = new ActionErrors();
        String name = request.getParameter("email");
        String pass = request.getParameter("password");
        String conpass = request.getParameter("conpassword");
        System.out.println(name+", "+pass+", "+conpass);
        if (name.trim().equals(""))
            ae.add("name", new ActionMessage("error.name.required"));
        if (pass.trim().equals(""))
            ae.add("pass", new ActionMessage("error.pass.required"));
        if (conpass.trim().equals("")) {
            ae.add("conpass", new ActionMessage("error.conpass.required"));
        }
        if (!conpass.trim().equals(pass.trim()))
            ae.add("notmatched", new ActionMessage("pass.conpass.notmatched"));
        return ae;
    }
}