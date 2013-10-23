package mail;

import play.mvc.Mailer;

public class Mails  extends Mailer{
	public Mails() {
		// TODO Auto-generated constructor stub
	}
	
	public static void welcome(String to , String message){
		setSubject("Welcome"+to);
		
	}
}
