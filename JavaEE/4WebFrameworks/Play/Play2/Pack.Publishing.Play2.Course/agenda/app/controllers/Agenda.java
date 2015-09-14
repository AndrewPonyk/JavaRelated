package controllers;

import java.util.ArrayList;
import java.util.List;

import controllers.auth.AgendaAuthenticator;
import models.Contact;
import play.data.Form;
import play.mvc.Controller;
import play.mvc.Result;
import play.mvc.Security.Authenticated;

// https://www.youtube.com/watch?v=96f6LxANLmI - one and hour tutorial with mongo !!!

@LogRequest // it like interceptor
@Authenticated(AgendaAuthenticator.class)
public class Agenda extends Controller{

	public Result list(){
		List<Contact> contacts = Contact.finder.all();

		return ok(views.html.list.render(contacts));
	}

	public Result show(Long id){
		Contact contact = Contact.finder.byId(id);
		if(contact == null){
			System.out.println("=========");
			return notFound();
		}
		return ok(views.html.show.render(contact));
	}

	public Result newContact(){
		Form<Contact> contactForm = Form.form(Contact.class);
		System.out.println("123");
		return ok(views.html.newContact.render(contactForm));
	}

	public Result createContact(){
		Form<Contact> formContact = Form.form(Contact.class).bindFromRequest();
		if(formContact.hasErrors()){
			return badRequest(views.html.newContact.render(formContact));
		}
		formContact.get().save();
		return redirect(routes.Agenda.list());
	}

	public Result tempAction(){
		List<String> names = new ArrayList<String>();
		names.add("Sasha");names.add("Dasha");
		return ok(views.html.tempviews.tempview2.render("Value from db", "another Value", names));
	}

}