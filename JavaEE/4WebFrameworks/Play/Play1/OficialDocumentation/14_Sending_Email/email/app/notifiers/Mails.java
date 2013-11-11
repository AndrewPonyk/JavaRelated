package notifiers;

import play.mvc.Mailer;

public class Mails  extends Mailer{
	public Mails() {
		// TODO Auto-generated constructor stub
	}
	
	public static void welcome(String to ){
		System.out.println("Sending email..");
		setSubject("Welcome"+to);
		addRecipient(to);
		setFrom("Me <me@me.com>");
		String user="andrew9999";
		send(user);

	}
}
