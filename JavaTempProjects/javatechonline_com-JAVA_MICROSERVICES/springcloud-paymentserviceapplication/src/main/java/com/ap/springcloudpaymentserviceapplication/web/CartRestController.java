package com.ap.springcloudpaymentserviceapplication.web;


import com.netflix.appinfo.InstanceInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@RestController
@RequestMapping("/payment")
public class CartRestController {

    @Autowired
    private DiscoveryClient discoveryClient;

    @Autowired
    private FeignCartServiceClient feignCartServiceClient;

    private RestTemplate restTemplate = new RestTemplate();

    @GetMapping("/data")
    public String getPaymentData(){
        //     * CALL EXTERNAL SERIVCE WITHOUT FEIGN (manually retrieve instance of CART-SERVICE and call it using resttemplate)
        //return "FROM PAYMENT-SERVICE : " + getCartInfo();

        return "FROM PAYMENT-SERVICE:" +feignCartServiceClient.getCartInfo();
    }

    /**
     * CALL EXTERNAL SERIVCE WITHOUT FEIGN (manually retrieve instance of CART-SERVICE and call it using resttemplate)
     * @return
     */
    private String getCartInfo(){
        List<ServiceInstance> siList = discoveryClient.getInstances("CART-SERVICE");
        final ServiceInstance serviceInstance = siList.get(0);
        String url = serviceInstance.getUri() +"/cart/getData";

        return  и еп .getForObject(url, String.class);
    }
}
