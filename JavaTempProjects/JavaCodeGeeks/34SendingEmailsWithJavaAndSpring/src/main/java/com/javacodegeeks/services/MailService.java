package com.javacodegeeks.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.mail.*;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

@Service
public class MailService {
    @Autowired
    private Session session;

    public void sendMail(String friendlyFrom, String to, String subject, String body){
        try {
            Transport transport = session.getTransport();
            InternetAddress addressFrom = new InternetAddress(friendlyFrom +
                    "<mail@gmail.com>"); // set friendly name  Letter will be from Company XYZ


            MimeMessage message = new MimeMessage(session);
            message.setFrom(addressFrom); // works without this
            message.setSubject(subject);
            message.setContent(body, "text/plain");
            message.addRecipient(Message.RecipientType.TO, new InternetAddress(to));

            transport.connect();
            Transport.send(message);
            transport.close();

        } catch (NoSuchProviderException e) {
            e.printStackTrace();
        } catch (AddressException e) {
            e.printStackTrace();
        } catch (MessagingException e) {
            e.printStackTrace();
        }

    }
}
