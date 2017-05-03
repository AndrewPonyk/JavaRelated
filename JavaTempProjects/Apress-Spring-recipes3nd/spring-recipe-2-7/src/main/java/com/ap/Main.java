package com.ap;

import org.springframework.context.ApplicationContext;
import org.springframework.context.support.GenericXmlApplicationContext;

import java.util.Locale;

/**
 * Created by andrii on 16.03.17.
 */
public class Main {
    public static void main(String[] args) {
        ApplicationContext context =
                new GenericXmlApplicationContext("beans.xml");

        String alert = context.getMessage("alert.checkout", null, Locale.US);
        String uaAlert = context.getMessage("alert.checkout", null, new Locale("uk", "UA"));
        String alertWithPlaceholder = context.getMessage("alert.checkout.placeholder", new Object[]{4}, Locale.US);

        System.out.println(alert);
        System.out.println(uaAlert);
        System.out.println(alertWithPlaceholder);
    }
}
