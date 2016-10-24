package spring.jms;

import java.util.Map;

public class JMSListener1 {

@SuppressWarnings("unchecked")
public void receive(Object message) {
		
		System.out.println(message);
		System.out.println("JMSListener1: Asynchronous onMessage is called");
		final Map<String, String> mapMessage = (Map<String, String>) message;		
		
		String key1 = mapMessage.get("key1");
		String key2 = mapMessage.get("key2");
		String key3 = mapMessage.get("key3");
		String key4 = mapMessage.get("key4");
				
		System.out.println("JMSListener1 mapMessage key1: " + key1);
		System.out.println("JMSListener1 mapMessage key2: " + key2);
		System.out.println("JMSListener1 mapMessage key3: " + key3);
		System.out.println("JMSListener1 mapMessage key4: " + key4);	
		
	}	
}
