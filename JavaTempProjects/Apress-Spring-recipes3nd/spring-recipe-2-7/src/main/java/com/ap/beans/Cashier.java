package com.ap.beans;

import org.springframework.context.MessageSource;

import java.io.IOException;
import java.util.Locale;

/**
 * Created by andrii on 16.03.17.
 */
public class Cashier {
    private MessageSource messageSource;
    public void setMessageSource(MessageSource messageSource) {
        this.messageSource = messageSource;
    }
    public void checkout()  {
        String alert = messageSource.getMessage("alert.checkout.placeholder",
                new Object[] { 9 },
                Locale.US);
        System.out.println(alert);
    }
}
