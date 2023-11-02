package com.crossairlines.services;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import com.crossairlines.form.SearchFlightForm;
import com.crossairlines.pojo.Airport;
import com.crossairlines.pojo.FlightDetails;

public class Services {
    public List<FlightDetails> getFlightDetails(SearchFlightForm sff) {
        List<FlightDetails> fdList = new ArrayList<FlightDetails>();

        return fdList;
    }

    public String getTime() {
        String time = "";
        Random rand = new Random();
        int hours = rand.nextInt(24);
        int min = rand.nextInt(60);
        time = hours + ":" + min;
        return time;
    }

    public String getArrivalTime(String time, double timetaken) {
        String timetk = timetaken + "";
        int hours = Integer.parseInt(timetk.split("\\.")[0]) + Integer.parseInt(time.split(":")[0]);
        int min = Integer.parseInt(timetk.split("\\.")[1]) + Integer.parseInt(time.split(":")[1]);
        if (min > 59) {
            min -= 60;
            hours++;
        }
        if (hours > 23) {
            hours -= 24;
        }

        return hours + ":" + min;
    }

//	public List<FlightDetails> getSearchedFlights(List<Airport> airportList, SearchFlightForm sff) {
//		List<FlightDetails> flightList = new ArrayList<FlightDetails>();
//		for (int i = 0; i < 5; i++) {
//			Iterator<Airport> itr = airportList.iterator();
//			while (itr.hasNext()) {
//				Airport airport = (Airport) itr.next();
//				if (airport.getOriginCity().trim().equals(sff.getOrigin().trim())
//						&& airport.getDestCity().trim().equals(sff.getDestination().trim())) {
//					System.out.println("id : " + airport.getAirportId());
//					FlightDetails flightDetails = new FlightDetails();
//					flightDetails.setOriginCity(airport.getOriginCity());
//					flightDetails.setDestinationCity(airport.getDestCity());
//					flightDetails.setOriginAirport(airport.getOriginAirport());
//					flightDetails.setDestinationAirport(airport.getDestinationAirport());
//					flightDetails.setDistance(airport.getDistance());
//					flightDetails.setDepartDate(sff.getDepartDate());
//					String time = getTime();
//					flightDetails.setArrivalTime(getArrivalTime(time, airport.getTimeTaken()));
//					flightDetails.setDepartTime(time);
//
//					double fare = (airport.getDistance() * airport.getTimeTaken());
//					if (sff.getClassType().trim().equals("Economy"))
//						fare += 1000;
//					if (sff.getClassType().trim().equals("Business"))
//						fare += 2000;
//					if (sff.getClassType().trim().equals("First"))
//						fare += 2500;
//					fare = sff.getAdultCount() * fare + sff.getChildCount() * (fare / 2)+new Random().nextInt(100);
//					flightDetails.setFare(Double.parseDouble(String.format("%.2f", fare)));
//					flightList.add(flightDetails);
//				}
//				if (airport.getOriginCity().trim().equals(sff.getDestination().trim())
//						&& airport.getDestCity().trim().equals(sff.getOrigin().trim())) {
//					System.out.println("id : " + airport.getAirportId());
//					FlightDetails flightDetails = new FlightDetails();
//					flightDetails.setOriginCity(airport.getDestCity());
//					flightDetails.setDestinationCity(airport.getOriginCity());
//					flightDetails.setOriginAirport(airport.getDestinationAirport());
//					flightDetails.setDestinationAirport(airport.getOriginAirport());
//					flightDetails.setDistance(airport.getDistance());
//					flightDetails.setDepartDate(sff.getDepartDate());
//					String time = getTime();
//					flightDetails.setArrivalTime(getArrivalTime(time, airport.getTimeTaken()));
//					flightDetails.setDepartTime(time);
//
//					double fare = (airport.getDistance() * airport.getTimeTaken());
//					if (sff.getClassType().trim().equals("Economy"))
//						fare += 1000;
//					if (sff.getClassType().trim().equals("Business"))
//						fare += 2000;
//					if (sff.getClassType().trim().equals("First"))
//						fare += 2500;
//					fare = sff.getAdultCount() * fare + sff.getChildCount() * (fare / 2);
//					flightDetails.setFare(Double.parseDouble(String.format("%.2f", fare)));
//					flightList.add(flightDetails);
//				}
//			}
//		}
//		return flightList;
//	}
}
