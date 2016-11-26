package com.ap.powermockHello;

public class ProcessNetworkResponceService {
    public String getDataFromGoogle(){
        RestClient restClient = new RestClient(10, new Object());

        return "(" + restClient.getSiteContent("google.com") + ")";
    }
}
