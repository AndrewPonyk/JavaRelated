package com;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.activation.FileDataSource;
import javax.mail.*;
import javax.mail.internet.*;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.Properties;

//basic usage of sonarqube
//mvn clean package sonar:sonar  -Dsonar.host.url=http://localhost:9000 -Dsonar.login=a6038e62294db47baf288d5eb4ea0083bd78ff01
public class JavaCoreSendMailApp {

    public static void main(String[] args) throws MessagingException {
        sendHtmlTable("", "bet notification", Arrays.asList("1.5000000000000000000000:" + new Date(), "second row"),
                "", "");
        //sendEmailWithAttachment("", "bet notification", "sample text", "", "");

    }

    private static void sendEmailWithAttachment(String to, String subject, String msg, String from, String password) throws MessagingException {
        // worked example from here (7 votes)
        //http://stackoverflow.com/questions/10509699/must-issue-a-starttls-command-first

        Session session = getSession(from, password);

        //can be added to debug "session.setDebug(true);"
        Transport transport = session.getTransport();
        InternetAddress addressFrom = new InternetAddress(from);

        Multipart multipart = new MimeMultipart();
        MimeBodyPart textPart = new MimeBodyPart();
        textPart.setText("Hello 9999");

        MimeBodyPart attachmentPart = new MimeBodyPart();
        DataSource fileDataSource = new FileDataSource("C:\\tmp\\dominicana_canoe.jpg");
        attachmentPart.setDataHandler(new DataHandler(fileDataSource));
        attachmentPart.setFileName("Dominicana.jpg");

        multipart.addBodyPart(textPart);
        multipart.addBodyPart(attachmentPart);


        MimeMessage message = new MimeMessage(session);
        message.setSender(addressFrom);
        message.setSubject(subject);
        message.setContent(multipart);
        message.addRecipient(Message.RecipientType.TO, new InternetAddress(to));

        transport.connect();
        Transport.send(message);
        transport.close();
    }

    private static void sendTextMessage(String to, String subject, String msg, String from, String password) throws MessagingException {
        Session session = getSession(from, password);

        // can be added "session.setDebug(true);"
        Transport transport = session.getTransport();
        InternetAddress addressFrom = new InternetAddress(from);
        MimeMessage message = new MimeMessage(session);
        message.setSender(addressFrom);
        message.setSubject(subject);
        message.setContent(msg, "text/plain");
        message.addRecipient(Message.RecipientType.TO, new InternetAddress(to));

        transport.connect();
        Transport.send(message);
        transport.close();
    }

    private static void sendHtmlTable(String to, String subject, Collection<String> rowsText, String from, String password) throws MessagingException {
        // worked example from here (7 votes)
        //http://stackoverflow.com/questions/10509699/must-issue-a-starttls-command-first


        Session session = getSession(from, password);

        // can be added "session.setDebug(true);"
        Transport transport = session.getTransport();
        InternetAddress addressFrom = new InternetAddress(from);


        MimeMessage messageWithHtml = new MimeMessage(session);
        messageWithHtml.setSubject(subject);
        Multipart mp = new MimeMultipart();
        MimeBodyPart htmlPart = new MimeBodyPart();

        StringBuilder tableHtml = new StringBuilder("<table style='border-collapse:collapse; border: 2px solid grey'>");
        rowsText.stream().forEach(e -> tableHtml.append(getTableRow(e)));
        tableHtml.append("</table>");
        htmlPart.setContent(tableHtml.toString(), "text/html");

        mp.addBodyPart(htmlPart);
        messageWithHtml.setContent(mp);
        messageWithHtml.addRecipient(Message.RecipientType.TO, new InternetAddress(to));


        Transport.send(messageWithHtml);
        transport.close();
    }

    private static String getTableRow(String text) {
        return "<tr><td style='border-collapse: collapse;border: 1px solid grey;padding:5px'>" + text + "</td></tr>";
    }

    private static Session getSession(String from, String password) {
        Properties props = new Properties();
        props.setProperty("mail.transport.protocol", "smtp");
        props.setProperty("mail.host", "smtp.gmail.com");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.port", "465");
        props.put("mail.debug", "false"); // !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
        props.put("mail.smtp.socketFactory.port", "465");
        props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
        props.put("mail.smtp.socketFactory.fallback", "false");
        return Session.getDefaultInstance(props,
                new Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(from, password);
                    }
                });
    }


////////////////////////////////////////////
// Good utils classes, and different security way: 1) No auth 2)TLS auth 3)SSL auth
//http://www.journaldev.com/2532/javamail-example-send-mail-in-java-smtp
}
