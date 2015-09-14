package views;

import static play.test.Helpers.*;
import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

import models.Contact;

import org.junit.Test;

import play.twirl.api.Html;

public class ListTemplateTest {

	@Test
	public void testTemplateList(){
		List<Contact> contacts = new ArrayList<Contact>();
		String name = "andrew";
		String email = "andrew@ukr.net";
		String phone = "093";
		Contact contact = new Contact("andrew", "andrew9999@ukr.net", "093");
		contacts.add(contact);

		Html html = views.html.list.render(contacts);

		assertEquals(html.contentType()	, "text/html");
		assertTrue(contentAsString(html).contains(contact.name));
		assertTrue(contentAsString(html).contains("Add new contact"));

	}

}
