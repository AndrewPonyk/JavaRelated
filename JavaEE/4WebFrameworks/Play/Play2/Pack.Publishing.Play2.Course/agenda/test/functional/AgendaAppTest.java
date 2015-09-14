package functional;

import static play.test.Helpers.*;

import org.junit.Test;

import play.libs.F.Promise;
import play.libs.ws.WS;
import play.libs.ws.WSResponse;
import scala.Unit;
import static org.junit.Assert.*;

public class AgendaAppTest {

	@Test
	public void newContactFormPage(){
		running(testServer(3333), new Runnable() {

			@Override
			public void run() {
				// this doesnt execute javascript !!!!
				WSResponse wsResponse = WS.url("http://localhost:3333/contacts/new").get().get(4000L);
				System.out.println(wsResponse.getBody());
				assertTrue(wsResponse.getBody().contains("email"));
			}
		});
	}
}
