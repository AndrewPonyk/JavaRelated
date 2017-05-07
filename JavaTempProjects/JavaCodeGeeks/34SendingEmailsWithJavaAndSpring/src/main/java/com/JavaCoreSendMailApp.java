package com;

import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import java.util.Properties;
import java.util.concurrent.TransferQueue;

/**
 * Created by andrii on 07.05.17.
 */
public class JavaCoreSendMailApp {
    public static void main(String[] args) throws MessagingException {
        // worked example from here (7 votes)
        //http://stackoverflow.com/questions/10509699/must-issue-a-starttls-command-first


        //7 Votes
        //smtp port and socketFactory has to be change

        String to = "";
        String subject = "subject";
        String msg = "email text....";
        final String from = "";
        final String password = "";


        Properties props = new Properties();
        props.setProperty("mail.transport.protocol", "smtp");
        props.setProperty("mail.host", "smtp.gmail.com");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.port", "465");
        props.put("mail.debug", "true");
        props.put("mail.smtp.socketFactory.port", "465");
        props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
        props.put("mail.smtp.socketFactory.fallback", "false");
        Session session = Session.getDefaultInstance(props,
                new javax.mail.Authenticator() {
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(from, password);
                    }
                });

        //session.setDebug(true);
        Transport transport = session.getTransport();
        InternetAddress addressFrom = new InternetAddress(from);

        MimeMessage message = new MimeMessage(session);
        message.setSender(addressFrom);
        message.setSubject(subject);
        message.setContent(msg, "text/plain");
        message.addRecipient(Message.RecipientType.TO, new InternetAddress(to));

        MimeMessage messageWithHtml = new MimeMessage(session);
        Multipart mp = new MimeMultipart();
        MimeBodyPart htmlPart = new MimeBodyPart();
        htmlPart.setContent("<b>bold</b>text", "text/html");
        mp.addBodyPart(htmlPart);
        messageWithHtml.setContent(mp);
        messageWithHtml.addRecipient(Message.RecipientType.TO, new InternetAddress(to));


        transport.connect();
        Transport.send(message);
        Transport.send(messageWithHtml);
        transport.close();
    }


////////////////////////////////////////////
// Good utils classes, and different security way: 1) No auth 2)TLS auth 3)SSL auth
//http://www.journaldev.com/2532/javamail-example-send-mail-in-java-smtp
}
