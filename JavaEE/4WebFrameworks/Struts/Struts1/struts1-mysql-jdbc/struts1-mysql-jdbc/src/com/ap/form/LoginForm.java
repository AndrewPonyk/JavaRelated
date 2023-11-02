package com.ap.form;

import org.apache.struts.action.ActionForm;

public class LoginForm extends ActionForm {
    private String user;
    private String password;
    private String nombre;

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getNombre() {
        return nombre;
    }
}
