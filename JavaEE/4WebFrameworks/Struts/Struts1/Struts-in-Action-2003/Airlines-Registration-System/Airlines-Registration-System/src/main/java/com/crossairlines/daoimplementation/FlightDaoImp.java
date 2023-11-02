package com.crossairlines.daoimplementation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.cfg.Configuration;
import org.hibernate.query.Query;

import com.crossairlines.dao.FlightDao;
import com.crossairlines.pojo.Airport;
import com.crossairlines.pojo.BookTicket;
import com.crossairlines.pojo.FlightDetails;
import com.crossairlines.pojo.Payment;

public class FlightDaoImp implements FlightDao{
    SessionFactory sf = null;
    public FlightDaoImp() {
        sf = new Configuration().configure().buildSessionFactory();
    }
    @Override
    public FlightDetails getFlightDetail(int id) {
        Session session = sf.openSession();
        Query<FlightDetails> query =  session.createQuery("from FlightDetails where flightDetailsId=:id");
        session.close();
        query.setParameter("id",id);
        return query.getSingleResult();
    }

    @Override
    public List<FlightDetails> getAllFlightDetails() {
        Session session = sf.openSession();
        Query<FlightDetails> query =  session.createQuery("from FlightDetails");
        return query.getResultList();
    }

    @Override
    public List<Airport> getAllAriports() {
        Session session = sf.openSession();
        Query<Airport> query =  session.createQuery("from Airport");
        return query.getResultList();
    }
    @Override
    public void addFlight(FlightDetails fd) {
        Session session = sf.openSession();
        Transaction tx = session.beginTransaction();
        session.save(fd);
        tx.commit();
        session.close();

    }
    @Override
    public HashMap<String,ArrayList< String>> getSourceAndDest() {
        HashMap<String, ArrayList<String>> sourceAndDest = new HashMap<String, ArrayList<String>>();
        Session session = sf.openSession();
        Query<FlightDetails> query = session.createQuery("from FlightDetails");
        //System.out.println(query.getResultList().get(0));
        for(Object fd : query.getResultList()){
            if(sourceAndDest.containsKey(((FlightDetails) fd).getOriginCity())){
                sourceAndDest.get(((FlightDetails) fd).getOriginCity()).add(	((FlightDetails) fd).getDestinationCity());
            }
            else {
                ArrayList<String> dest = new ArrayList<String>();
                dest.add(((FlightDetails) fd).getDestinationCity());
                sourceAndDest.put(((FlightDetails) fd).getOriginCity(), dest);
            }

        }
        return sourceAndDest;
    }
    @Override
    public void confirmBooking(BookTicket bookTicket, Payment payment) {
        Session session = sf.openSession();
        Transaction tx = session.beginTransaction();
        session.save(bookTicket);
        session.save(payment);
        tx.commit();
        session.close();
    }

}