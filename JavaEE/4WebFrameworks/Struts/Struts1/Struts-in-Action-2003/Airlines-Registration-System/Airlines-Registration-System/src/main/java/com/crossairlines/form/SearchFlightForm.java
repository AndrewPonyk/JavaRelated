package com.crossairlines.form;

import org.apache.struts.action.ActionForm;

public class SearchFlightForm extends ActionForm {

    private static final long serialVersionUID = -508650572329493602L;
    private String flightType;
    private String origin;
    private String destination;
    private String departDate;
    private String returnDate;
    private String multicity;


    public String getFlightType() {
        return flightType;
    }
    public void setFlightType(String flightType) {
        this.flightType = flightType;
    }
    public String getOrigin() {
        return origin;
    }
    public void setOrigin(String origin) {
        this.origin = origin;
    }
    public String getDestination() {
        return destination;
    }
    public void setDestination(String destination) {
        this.destination = destination;
    }
    public String getDepartDate() {
        return departDate;
    }
    public void setDepartDate(String departDate) {
        this.departDate =departDate;
    }
    public String getReturnDate() {
        return returnDate;
    }
    public void setReturnDate(String returnDate) {

        this.returnDate = returnDate;
    }
    public String getMulticity() {
        return multicity;
    }
    public void setMulticity(String multicity) {
        this.multicity = multicity;
    }
    @Override
    public String toString() {
        return "SearchFlightForm [flightType=" + flightType + ", origin=" + origin + ", destination=" + destination
                + ", departDate=" + departDate + ", returnDate=" + returnDate + ", multicity=" + multicity + "]";
    }




}