package com.ap.services;

import javax.jws.WebMethod;
import javax.jws.WebService;
import java.util.Date;



@WebService
public interface DateService {
    @WebMethod
    public String getDate(int n);
    
    @WebMethod
    public String getCityLviv();
}
