package com.ap;

import com.ap.beans.Cashier;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.GenericXmlApplicationContext;

/**
 * Created by andrii on 16.03.17.
 */
public class Main2 {
    public static void main(String[] args) {
        ApplicationContext context =
                new GenericXmlApplicationContext("beans.xml");

        Cashier cashier = context.getBean("cashier", Cashier.class);
        cashier.checkout();
    }
}
