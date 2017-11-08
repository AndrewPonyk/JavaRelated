package com.javacodegeeks.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.util.logging.Level;
import java.util.logging.Logger;

@Service
public class MailService {
    Logger logger = Logger.getLogger(MailService.class.getName());

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

        } catch (MessagingException e) {
           logger.log(Level.ALL, e.getMessage());
        }
    }
}
