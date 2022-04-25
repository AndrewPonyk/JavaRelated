package com.ap.springcloudcartserviceapplication;


import org.springframework.cloud.openfeign.FeignClient;

@FeignClient()
public interface FeignCartServiceClient {

    public String getCartInfo();
}
