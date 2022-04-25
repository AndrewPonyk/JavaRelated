package com.ap.springcloudpaymentserviceapplication.web;


import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

@FeignClient("CART-SERVICE")
public interface FeignCartServiceClient {

    @GetMapping("/cart/getData")
    public String getCartInfo();
}
