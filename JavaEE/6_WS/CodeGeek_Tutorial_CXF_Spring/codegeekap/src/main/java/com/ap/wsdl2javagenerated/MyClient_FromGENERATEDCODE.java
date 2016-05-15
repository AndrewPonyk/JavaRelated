package com.ap.wsdl2javagenerated;

import com.ap.services.*;

import java.net.MalformedURLException;
import java.net.URL;

/**
 * Created by andrii on 08.05.16.
 */
public class MyClient_FromGENERATEDCODE {
    public static void main(String[] args) throws MalformedURLException {
        System.out.println("My client from generated code");

        // this works because inside the default wsdl location is hardcoded (http://localhost:8080/codegeekap/services/dateservice?wsdl)
        com.ap.services.DateService dateServiceImplService = new DateServiceImplService()
                .getDateServiceImplPort();

        //pass custom wsdl location
        com.ap.services.DateService dateServiceImplService2 = new DateServiceImplService(new URL("http://localhost:8080/codegeekap/services/dateservice?wsdl"))
                .getDateServiceImplPort();

        System.out.println(dateServiceImplService.getDate(10000));
        System.out.println(dateServiceImplService2.getDate(1));
    }
}
