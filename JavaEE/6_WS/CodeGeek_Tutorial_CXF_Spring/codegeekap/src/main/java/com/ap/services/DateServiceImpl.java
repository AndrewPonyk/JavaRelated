package com.ap.services;

import javax.jws.WebService;
import java.util.Date;
import javax.jws.WebMethod;

@WebService(endpointInterface = "com.ap.services.DateService")
public class DateServiceImpl implements DateService {
    public String getDate(int n) {
        return new Date() + " ;" + n;
    }

    public String getCityLviv() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    } 
}
