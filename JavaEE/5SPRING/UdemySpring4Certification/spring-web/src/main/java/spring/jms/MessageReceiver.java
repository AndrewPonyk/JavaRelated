package spring.jms;

import org.springframework.jms.core.JmsTemplate;


public class MessageReceiver {
	
	 private JmsTemplate jmsTemplate;
	 
	 public JmsTemplate getJmsTemplate() {
		return jmsTemplate;
	 }
	 public void setJmsTemplate(JmsTemplate jmsTemplate) {
		this.jmsTemplate = jmsTemplate;
	 }
}
