package controllers;

import static org.junit.Assert.*;
import static play.test.Helpers.*;
import models.Contact;

import org.junit.Test;

import play.mvc.Result;

public class AgendaTest {

	@Test
	public void list() {
		running(fakeApplication(inMemoryDatabase()), new Runnable() {
			public void run() {
				String name = "andrew";
				String email = "andrew@ukr.net";
				String phone = "093";

				Contact contact = new Contact(name, email, phone);
				contact.save();

				Result result = new Agenda().list();
				assertEquals(status(result), OK);
				assertEquals(contentType(result), "text/html");
				assertTrue(contentAsString(result).contains(contact.name));
			}
		});
	}

}
