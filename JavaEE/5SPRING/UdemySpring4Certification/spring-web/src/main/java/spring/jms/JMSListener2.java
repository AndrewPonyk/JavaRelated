package spring.jms;

import javax.jms.JMSException;
import javax.jms.MapMessage;
import javax.jms.Message;
import javax.jms.MessageListener;

public class JMSListener2 implements MessageListener {

	public void onMessage(Message message) {
		System.out.println(message);
		System.out.println("JMSListener2: Asynchronous onMessage is called");
		final MapMessage mapMessage = (MapMessage) message;
		try {
			String key1 = mapMessage.getString("key1");
			String key2 = mapMessage.getString("key2");
			String key3 = mapMessage.getString("key3");
			String key4 = mapMessage.getString("key4");
			
			System.out.println("JMSListener2 mapMessage key1: " + key1);
			System.out.println("JMSListener2 mapMessage key2: " + key2);
			System.out.println("JMSListener2 mapMessage key3: " + key3);
			System.out.println("JMSListener2 mapMessage key4: " + key4);
			
			System.out.println("JMSListener2 message idJMS: " + message.getStringProperty("idJMS"));
			System.out.println("JMSListener2 message JMSCorrelationID: " + message.getJMSCorrelationID());
		} catch (JMSException e) {
			e.printStackTrace();
		}
		
	}
	


}
