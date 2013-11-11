package controllers;

import play.*;
import play.libs.Mail;
import play.mvc.*;

import java.util.*;

import notifiers.Mails;

import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.HtmlEmail;
import org.apache.commons.mail.SimpleEmail;


public class Application extends Controller {

    public static void index() {
        render();
    }
    
    public static void sendMail(String to,String message){
    	System.out.println(to);
    	System.out.println(message);
    	
    	// there is also SimpleEmail class , but if you want to send html content we will use HtmlEmail
    	HtmlEmail email=new HtmlEmail();
    	try {
			email.setFrom("andrew.ponuk9999@gmail.com");
			email.addTo(to);
			email.setSubject("subject");
			
			email.setHtmlMsg("<html><h3>Message header</h3>  <div>"+message+"</div></html>");
			
			email.setTextMsg(message); // simple plain
			
			
			// set the alternative message
			//email.setMsg("Your email client does not support HTML, too bad :(");
			
			
			Mail.send(email); 
			
		} catch (EmailException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    	
    	renderText("OK");
    }
    
    public static void sendMailUsingTemplate(String to){
    	Mails.welcome(to);
    	index();	
    }

}