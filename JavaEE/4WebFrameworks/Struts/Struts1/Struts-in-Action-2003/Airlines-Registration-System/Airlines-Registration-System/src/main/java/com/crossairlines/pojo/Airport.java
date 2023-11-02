
package com.crossairlines.pojo;

public class Airport {
    private int airportId;
    private String originCity;
    private String destCity;
    private String originAirport;
    private String destinationAirport;
    private double distance;
    private double timeTaken;
    public int getAirportId() {
        return airportId;
    }
    public void setAirportId(int airportId) {
        this.airportId = airportId;
    }
    public String getOriginCity() {
        originCity = originCity.replace("�"," ");
        originCity = originCity.replace("\"", "");
        return originCity;
    }
    public void setOriginCity(String originCity) {
        this.originCity = originCity;
    }
    public String getDestCity() {
        return destCity;
    }
    public void setDestCity(String destCity) {
        destCity = destCity.replace("�"," ");
        destCity = destCity.replace("\"", "");
        this.destCity = destCity;
    }
    public String getOriginAirport() {
        originAirport = originAirport.replace("\"", "");
        return originAirport;
    }
    public void setOriginAirport(String origin) {
        origin = origin.replace("\"", "");
        this.originAirport = origin;
    }
    public String getDestinationAirport() {
        return destinationAirport;
    }
    public void setDestinationAirport(String destination) {
        destination = destination.replace("\"", "");
        this.destinationAirport = destination;
    }
    public double getDistance() {
        return distance;
    }
    public void setDistance(double distance) {
        this.distance = distance;
    }
    public double getTimeTaken() {
        return timeTaken;
    }
    public void setTimeTaken(double timeTaken) {
        this.timeTaken = timeTaken;
    }
    @Override
    public String toString() {
        return "\nAirport [airportId=" + airportId + ", originCity=" + originCity + ", destCity=" + destCity
                + ", originAirport=" + originAirport + ", destinationAirport=" + destinationAirport + ", distance="
                + distance + ", timeTaken=" + timeTaken + "]\n";
    }




}