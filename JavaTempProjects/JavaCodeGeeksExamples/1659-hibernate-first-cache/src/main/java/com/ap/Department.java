package com.ap;

import java.io.Serializable;

/**
 * @author Andres.Cespedes
 * @version 1.0 $Date: 11/02/2015
 * @since 1.7
 *
 */
public class Department implements Serializable {

    private static final long serialVersionUID = 1997660946109705991L;
    private int idDepartment;
    private String name;

    public Department() {
    }

    public Department(int idDepartment, String name) {
        this.idDepartment = idDepartment;
        this.name = name;
    }

    /**
     * @return the idDepartment
     */
    public int getIdDepartment() {
        return idDepartment;
    }

    /**
     * @param idDepartment
     *            the idDepartment to set
     */
    public void setIdDepartment(int idDepartment) {
        this.idDepartment = idDepartment;
    }

    /**
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * @param name
     *            the name to set
     */
    public void setName(String name) {
        this.name = name;
    }

}