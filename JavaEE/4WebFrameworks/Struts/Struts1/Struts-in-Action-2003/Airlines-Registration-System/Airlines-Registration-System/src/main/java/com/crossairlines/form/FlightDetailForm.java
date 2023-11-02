package com.crossairlines.form;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.apache.struts.action.ActionForm;

public class FlightDetailForm extends ActionForm {
    /**
     *
     */
    private static final long serialVersionUID = 1833754074450606509L;
    private int flightDetailsId;
    private String fromCountry;
    private String destCountry;
    private String originCity;
    private String destinationCity;
    private String originAirport;
    private String destinationAirport;
    private Date departDate;
    private String departDatet;
    private String departTime;
    private Date arrivalDate;
    private String arrivalDatet;
    private String arrivalTime;
    private double economyFare;
    private double businessFare;
    private double firstClassFare;
    private String company;
    private String flightType;
    private double timeTaken;
    private int size;
    private int seatsAvailabe;
    private int users;
    private Date bookingDate;
    private int userId;



    public String getDepartDatet() {
        return departDatet;
    }
    public void setDepartDatet(String departDatet) {
        try {
            this.departDate = new SimpleDateFormat("yyyy-MM-dd").parse(departDatet);
        } catch (ParseException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        this.departDatet = departDatet;
    }
    public String getArrivalDatet() {
        return arrivalDatet;
    }
    public void setArrivalDatet(String arrivalDatet) {
        try {
            this.arrivalDate = new SimpleDateFormat("yyyy-MM-dd").parse(arrivalDatet);
        } catch (ParseException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        this.arrivalDatet = arrivalDatet;
    }
    public int getSize() {
        return size;
    }
    public void setSize(int size) {
        this.size = size;
    }
    public int getSeatsAvailabe() {
        return seatsAvailabe;
    }
    public void setSeatsAvailabe(int seatsAvailabe) {
        this.seatsAvailabe = seatsAvailabe;
    }
    public int getUsers() {
        return users;
    }
    public void setUsers(int users) {
        this.users = users;
    }
    public int getUserId() {
        return userId;
    }
    public void setUserId(int userId) {
        this.userId = userId;
    }
    public Date getBookingDate() {
        return bookingDate;
    }
    public void setBookingDate(String bookingDate) {

        try {
            this.bookingDate =new SimpleDateFormat("yyyy-MM-dd").parse(bookingDate);
        } catch (ParseException e) {
            e.printStackTrace();
        }
    }
    public int getFlightDetailsId() {
        return flightDetailsId;
    }
    public void setFlightDetailsId(int flightDetailsId) {
        this.flightDetailsId = flightDetailsId;
    }
    public String getOriginCity() {
        return originCity;
    }
    public void setOriginCity(String originCity) {
        this.originCity = originCity;
    }
    public String getDestinationCity() {
        return destinationCity;
    }
    public void setDestinationCity(String destinationCity) {
        this.destinationCity = destinationCity;
    }
    public String getOriginAirport() {
        return originAirport;
    }
    public void setOriginAirport(String originAirport) {
        this.originAirport = originAirport;
    }
    public String getDestinationAirport() {
        return destinationAirport;
    }
    public void setDestinationAirport(String destinationAirport) {
        this.destinationAirport = destinationAirport;
    }
    public String getFromCountry() {
        return fromCountry;
    }
    public void setFromCountry(String fromCountry) {
        this.fromCountry = fromCountry;
    }
    public String getDestCountry() {
        return destCountry;
    }
    public void setDestCountry(String destCountry) {
        this.destCountry = destCountry;
    }
    public Date getDepartDate() {
        return departDate;
    }
    public void setDepartDate(Date departDate) {
        this.departDate = departDate;
    }
    public String getDepartTime() {
        return departTime;
    }
    public void setDepartTime(String departTime) {
        this.departTime = departTime;
    }
    public Date getArrivalDate() {
        return arrivalDate;
    }
    public void setArrivalDate(Date arrivalDate) {
        this.arrivalDate = arrivalDate;
    }
    public String getArrivalTime() {
        return arrivalTime;
    }
    public void setArrivalTime(String arrivalTime) {
        this.arrivalTime = arrivalTime;
    }
    public double getEconomyFare() {
        return economyFare;
    }
    public void setEconomyFare(double economyFare) {
        this.economyFare = economyFare;
    }
    public double getBusinessFare() {
        return businessFare;
    }
    public void setBusinessFare(double businessFare) {
        this.businessFare = businessFare;
    }
    public double getFirstClassFare() {
        return firstClassFare;
    }
    public void setFirstClassFare(double firstClassFare) {
        this.firstClassFare = firstClassFare;
    }
    public String getCompany() {
        return company;
    }
    public void setCompany(String company) {
        this.company = company;
    }
    public String getFlightType() {
        return flightType;
    }
    public void setFlightType(String flightType) {
        this.flightType = flightType;
    }
    public double getTimeTaken() {
        return timeTaken;
    }
    public void setTimeTaken(double timeTaken) {
        this.timeTaken = timeTaken;
    }
    public void setBookingDate(Date bookingDate) {
        this.bookingDate = bookingDate;
    }
    @Override
    public String toString() {
        return "FlightDetails [flightDetailsId=" + flightDetailsId + ", fromCountry=" + fromCountry + ", destCountry="
                + destCountry + ", originCity=" + originCity + ", destinationCity=" + destinationCity
                + ", originAirport=" + originAirport + ", destinationAirport=" + destinationAirport + ", departDate="
                + departDate + ", departTime=" + departTime + ", arrivalDate=" + arrivalDate + ", arrivalTime="
                + arrivalTime + ", economyFare=" + economyFare + ", businessFare=" + businessFare + ", firstClassFare="
                + firstClassFare + ", company=" + company + ", flightType=" + flightType + ", timeTaken=" + timeTaken
                + ", size=" + size + ", seatsAvailabe=" + seatsAvailabe + ", users=" + users + ", bookingDate="
                + bookingDate + ", userId=" + userId + "]";
    }

}