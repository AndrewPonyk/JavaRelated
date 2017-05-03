package com.ap;

import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;

import java.util.Date;

public class CheckoutListener implements ApplicationListener {

    @Override
    public void onApplicationEvent(ApplicationEvent event) {
        System.out.println(event.getClass());
        if (event instanceof CheckoutEvent) {
            Date time = ((CheckoutEvent) event).getTime();

            System.out.println("Checkout event [" + time + "]");
        }
    }
}
