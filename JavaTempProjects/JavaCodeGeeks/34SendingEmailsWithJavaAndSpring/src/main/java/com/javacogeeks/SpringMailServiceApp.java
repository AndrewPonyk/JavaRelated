package com.javacogeeks;

import com.javacodegeeks.services.MailService;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import javax.mail.*;
import java.io.Closeable;
import java.io.IOException;
import java.time.LocalDateTime;


// It wasn't working on gmail (Auth exception)
//http://stackoverflow.com/questions/25341198/javax-mail-authenticationfailedexception-is-thrown-while-sending-email-in-java

/**I had the same problem !!

This is how i Solved.. google prevent it for security purpose..

Go Login from Your browser from that email.. i had problem with google if it is google go here : https://www.google.com/settings/security/lesssecureapps

you will see turn off or turn on. Click "Turn on"

Then try your code again it will work, worked for me
 */
public class SpringMailServiceApp
{
    public static void main( String[] args ) throws IOException {
        ApplicationContext context = new ClassPathXmlApplicationContext("beans.xml");

        MailService mailService = context.getBean("mailService", MailService.class);
        mailService.sendMail("Friendly company name", "andrew9999@ukr.net","From java 1",
                "Text: " + LocalDateTime.now().toString());
        ((Closeable)context).close();
    }
}
