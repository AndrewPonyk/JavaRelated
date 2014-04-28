package javamail;

import com.sun.mail.smtp.SMTPTransport;
import java.security.Security;
import java.util.Date;
import java.util.Properties;

import javax.activation.DataHandler;
import javax.activation.FileDataSource;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

public class JavaSendMail {
	private JavaSendMail() {
	}

	public static void main(String[] args) {
		System.out.println("Sending email");

		try {
			JavaSendMail.send("andrew.ponuk9999", "Ek***f12",
					"andrew9999@ukr.net", "", "Java Mail with Attached file",
					"Look inside file", System.getProperty("user.dir") + "/downloads/Lenta_za_lentoju_Rington.mp3");
		} catch (MessagingException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Send email using GMail SMTP server.
	 * 
	 * @param username
	 *            GMail username
	 * @param password
	 *            GMail password
	 * @param recipientEmail
	 *            TO recipient
	 * @param title
	 *            title of the message
	 * @param message
	 *            message to be sent
	 * @throws AddressException
	 *             if the email address parse failed
	 * @throws MessagingException
	 *             if the connection is dead or not in the connected state or if
	 *             the message is not a MimeMessage
	 */
	public static void send(final String username, final String password,
			String recipientEmail, String title, String message)
			throws AddressException, MessagingException {
		JavaSendMail.send(username, password, recipientEmail, "", title,
				message, null);
	}

	/*
	 * @param username GMail username
	 * 
	 * @param password GMail password
	 * 
	 * @param recipientEmail TO recipient
	 * 
	 * @param ccEmail CC recipient. Can be empty if there is no CC recipient
	 * 
	 * @param title title of the message
	 * 
	 * @param message message to be sent
	 * 
	 * @throws AddressException if the email address parse failed
	 * 
	 * @throws MessagingException if the connection is dead or not in the
	 * connected state or if the message is not a MimeMessage
	 */
	public static void send(final String username, final String password,
			String recipientEmail, String ccEmail, String title,
			String message, String filename) throws AddressException,
			MessagingException {
		Security.addProvider(new com.sun.net.ssl.internal.ssl.Provider());
		final String SSL_FACTORY = "javax.net.ssl.SSLSocketFactory";

		// Get a Properties object
		Properties props = System.getProperties();
		props.setProperty("mail.smtps.host", "smtp.gmail.com");
		props.setProperty("mail.smtp.socketFactory.class", SSL_FACTORY);
		props.setProperty("mail.smtp.socketFactory.fallback", "false");
		props.setProperty("mail.smtp.port", "465");
		props.setProperty("mail.smtp.socketFactory.port", "465");
		props.setProperty("mail.smtps.auth", "true");

		/*
		 * If set to false, the QUIT command is sent and the connection is
		 * immediately closed. If set to true (the default), causes the
		 * transport to wait for the response to the QUIT command.
		 * 
		 * ref :
		 * http://java.sun.com/products/javamail/javadocs/com/sun/mail/smtp
		 * /package-summary.html
		 * http://forum.java.sun.com/thread.jspa?threadID=5205249 smtpsend.java
		 * - demo program from javamail
		 */
		props.put("mail.smtps.quitwait", "false");

		Session session = Session.getInstance(props, null);

		// -- Create a new message --
		final MimeMessage msg = new MimeMessage(session);

		// -- Set the FROM and TO fields --
		msg.setFrom(new InternetAddress(username + "@gmail.com"));
		msg.setRecipients(Message.RecipientType.TO,
				InternetAddress.parse(recipientEmail, false));

		if (ccEmail.length() > 0) {
			msg.setRecipients(Message.RecipientType.CC,
					InternetAddress.parse(ccEmail, false));
		}

		msg.setSubject(title);
		msg.setText(message, "utf-8");
		msg.setSentDate(new Date());

		// Создание и заполнение первой части
		MimeBodyPart p1 = new MimeBodyPart();
		p1.setText(message, "UTF-8");


		// Создание экземпляра класса Multipart. Добавление частей сообщения в
		// него.
		Multipart mp = new MimeMultipart();
		mp.addBodyPart(p1);

		if (filename != null && filename.length() > 1) {
			  // Создание второй части
	        MimeBodyPart p2 = new MimeBodyPart();
	 
	        // Добавление файла во вторую часть
	        FileDataSource fds = new FileDataSource(filename);
	        p2.setDataHandler(new DataHandler(fds));
	        p2.setFileName(fds.getName());
			mp.addBodyPart(p2);
		}
		
		msg.setContent(mp);
		
		SMTPTransport t = (SMTPTransport) session.getTransport("smtps");

		t.connect("smtp.gmail.com", username, password);
		t.sendMessage(msg, msg.getAllRecipients());
		t.close();
	}

}