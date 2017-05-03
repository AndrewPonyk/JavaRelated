package com.javacogeeks;

import com.javacodegeeks.services.MailService;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.io.Closeable;
import java.io.IOException;

/**
 * Hello world!
 *
 */
public class MailServiceApp
{
    public static void main( String[] args ) throws IOException {
        ApplicationContext context = new ClassPathXmlApplicationContext("beans.xml");

        MailService mailService = context.getBean("mailService", MailService.class);
        mailService.sendMail("andrew.ponuk9999@gmail.com", "andrew9999@ukr.net","From java 1","123");

        ((Closeable)context).close();
    }
}
