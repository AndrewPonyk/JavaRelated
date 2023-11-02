package com.crossairlines.form;

import org.apache.struts.action.ActionForm;

public class BookTicketForm extends ActionForm{
    /**
     *
     */
    private static final long serialVersionUID = -1487913093070083538L;
    private int id;
    private int adultCount;
    private int childCount;
    private int finalFare;

    private String classType;


    public int getAdultCount() {
        return adultCount;
    }
    public void setAdultCount(int adultCount) {
        System.out.println("hi");
        this.adultCount = adultCount;
    }
    public int getChildCount() {
        return childCount;
    }
    public void setChildCount(int childCount) {
        this.childCount = childCount;
    }
    public int getFinalFare() {
        return finalFare;
    }
    public void setFinalFare(int finalFare) {
        System.out.println(finalFare);

        this.finalFare = finalFare;
    }
    public int getId() {
        return id;
    }
    public void setId(int id) {
        this.id = id;
    }
    public String getClassType() {
        return classType;
    }
    public void setClassType(String classType) {
        this.classType = classType;
    }
    @Override
    public String toString() {
        return "BookTicketForm [id=" + id + ", adultCount=" + adultCount + ", childCount=" + childCount + ", finalFare="
                + finalFare + ", classType=" + classType + "]";
    }


}