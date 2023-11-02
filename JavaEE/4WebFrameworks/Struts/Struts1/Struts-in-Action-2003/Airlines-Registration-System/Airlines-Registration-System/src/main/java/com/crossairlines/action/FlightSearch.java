package com.crossairlines.action;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.actions.DispatchAction;

import com.crossairlines.dao.FlightDao;
import com.crossairlines.daoimplementation.FlightDaoImp;
import com.crossairlines.form.SearchFlightForm;
import com.crossairlines.pojo.FlightDetails;
import com.crossairlines.services.Services;

public class FlightSearch extends DispatchAction{
    Services service = new Services();
    FlightDao flightDao = new FlightDaoImp();

    public ActionForward searchFlight(ActionMapping mapping, ActionForm form, HttpServletRequest request,
                                      HttpServletResponse response) throws Exception {
        SearchFlightForm sff = (SearchFlightForm) form;
        System.out.println(sff);
        List<FlightDetails> flightDetails = new ArrayList<FlightDetails>();
        List<FlightDetails> altFlightDetails = new ArrayList<FlightDetails>();
        List<FlightDetails> flightList = flightDao.getAllFlightDetails();
        System.out.println(flightList);
        for(FlightDetails fd: flightList) {
            if(fd.getOriginCity().equals(sff.getOrigin()) && sff.getDestination().equals(fd.getDestinationCity()) ){
                if(new SimpleDateFormat("yyyy-MM-dd").parse(sff.getDepartDate()).compareTo(fd.getDepartDate()) == 0) {
                    flightDetails.add(fd);
                }
                else {
                    altFlightDetails.add(fd);
                }
            }
        }
        request.setAttribute("gotFlights", flightDetails);
        request.setAttribute("gotAltFlights", altFlightDetails);

        return mapping.findForward("success");
    }



}