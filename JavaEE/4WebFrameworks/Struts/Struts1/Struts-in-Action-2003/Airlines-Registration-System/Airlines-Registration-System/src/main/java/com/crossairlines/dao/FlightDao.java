package com.crossairlines.dao;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.crossairlines.pojo.Airport;
import com.crossairlines.pojo.BookTicket;
import com.crossairlines.pojo.FlightDetails;
import com.crossairlines.pojo.Payment;

public interface FlightDao {
    public FlightDetails getFlightDetail(int id);
    public List<FlightDetails> getAllFlightDetails();
    public List<Airport> getAllAriports();
    public void addFlight(FlightDetails fd);
    public HashMap<String,ArrayList<String>> getSourceAndDest();
    public void confirmBooking(BookTicket bookTicket, Payment payment);
}