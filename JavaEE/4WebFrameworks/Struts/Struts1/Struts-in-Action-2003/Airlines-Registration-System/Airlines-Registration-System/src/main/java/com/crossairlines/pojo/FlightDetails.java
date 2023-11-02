package com.crossairlines.pojo;

import java.util.Date;

public class FlightDetails {
    private int flightDetailsId;
    private String fromCountry;
    private String destCountry;
    private String originCity;
    private String destinationCity;
    private String originAirport;
    private String destinationAirport;
    private Date departDate;
    private String departTime;
    private Date arrivalDate;
    private String arrivalTime;
    private double economyFare;
    private double businessFare;
    private double firstClassFare;
    private String company;
    private String flightType;
    private double timeTaken;
    private int seatsAvailabe=100;



    public int getSeatsAvailabe() {
        return seatsAvailabe;
    }
    public void setSeatsAvailabe(int seatsAvailabe) {
        this.seatsAvailabe = seatsAvailabe;
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

    @Override
    public String toString() {
        return "FlightDetails [flightDetailsId=" + flightDetailsId + ", fromCountry=" + fromCountry + ", destCountry="
                + destCountry + ", originCity=" + originCity + ", destinationCity=" + destinationCity
                + ", originAirport=" + originAirport + ", destinationAirport=" + destinationAirport + ", departDate="
                + departDate + ", departTime=" + departTime + ", arrivalDate=" + arrivalDate + ", arrivalTime="
                + arrivalTime + ", economyFare=" + economyFare + ", businessFare=" + businessFare + ", firstClassFare="
                + firstClassFare + ", company=" + company + ", flightType=" + flightType + ", timeTaken=" + timeTaken
                + ", seatsAvailabe=" + seatsAvailabe + "]";
    }

}