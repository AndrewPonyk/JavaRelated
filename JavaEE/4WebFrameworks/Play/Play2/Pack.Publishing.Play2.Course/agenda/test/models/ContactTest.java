package models;

import static org.junit.Assert.*;

import org.junit.Test;

import static play.test.Helpers.*;
import static org.junit.Assert.*;

public class ContactTest {

	@Test
	public void testContactModel() {
		assertEquals(3, 3);
		running(fakeApplication(inMemoryDatabase()), new Runnable() {

			@Override
			public void run() {
				String name = "andrew";
				String email = "andrew@ukr.net";
				String phone = "093";

				Contact contact = new Contact(name,email,phone);
				contact.save();

				Contact byId = Contact.finder.byId(contact.id);

				assertEquals(contact.name, byId.name);
				assertEquals(contact.email, byId.email);
				assertEquals(contact.phone, byId.phone);
			}
		});
	}

}
