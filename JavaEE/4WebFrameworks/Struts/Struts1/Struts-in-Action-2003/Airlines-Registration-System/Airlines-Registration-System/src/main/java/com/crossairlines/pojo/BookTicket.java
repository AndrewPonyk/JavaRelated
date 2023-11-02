package com.crossairlines.pojo;

import java.util.Date;

public class BookTicket {
    private int bookingId;
    private Date bookingDate = new Date();
    private FlightDetails flightDetails;
    private User user;
    private Payment payment;
    private int adultCount;
    private int childCount;
    private int finalFare;

    private String classType;




    public int getAdultCount() {
        return adultCount;
    }
    public void setAdultCount(int adultCount) {
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
        this.finalFare = finalFare;
    }
    public String getClassType() {
        return classType;
    }
    public void setClassType(String classType) {
        this.classType = classType;
    }
    public Date getBookingDate() {
        return bookingDate;
    }
    public void setBookingDate(Date bookingDate) {
        this.bookingDate = new Date();
    }
    public int getBookingId() {
        return bookingId;
    }
    public void setBookingId(int bookingId) {
        this.bookingId = bookingId;
    }
    public FlightDetails getFlightDetails() {
        return flightDetails;
    }
    public void setFlightDetails(FlightDetails flightDetails) {
        this.flightDetails = flightDetails;
    }
    public User getUser() {
        return user;
    }
    public void setUser(User user) {
        this.user = user;
    }
    public Payment getPayment() {
        return payment;
    }
    public void setPayment(Payment payment) {
        this.payment = payment;
    }

}