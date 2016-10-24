package spring.controller;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.security.RolesAllowed;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.ModelAndView;

import spring.jms.MessageReceiver;
import spring.jms.MessageSender;


@Controller
public class MessageController {

	  @Autowired
	  private MessageSender messageSender;  
	  
	  @Autowired
	  private MessageReceiver messageReceiver; 
	
	  @RolesAllowed(value={"ROLE_ADMIN"})
	  @RequestMapping(value="/sendJMSMessage", method=RequestMethod.GET)
	  public ModelAndView sendJMSMessage() {
		  
		  System.out.println("MessageController sendJMSMessage is called with ADMIN ROLE");
		  Map<String, String> messageMap = new HashMap<String, String>();
		  messageMap.put("key1", "tunatore@gmail.com");
		  messageMap.put("key2", "tunatore@outlook.com");
		  messageMap.put("key3", "spring framework");
		  messageMap.put("key4", "udemy");
		  messageSender.send(messageMap);
		  
		  return new ModelAndView("/admin/adminsecured");
		  
	  }
	  
	  @RolesAllowed(value={"ROLE_ADMIN"})
	  @RequestMapping(value="/sendJMSMessageAsync", method=RequestMethod.GET)
	  public ModelAndView sendJMSMessageAsync() {
		  
		  System.out.println("MessageController sendJMSMessageAsync is called with ADMIN ROLE");		  
		  Map<String, String> messageMap2 = new HashMap<String, String>();
		  messageMap2.put("key1", "topic tunatore@gmail.com");
		  messageMap2.put("key2", "topic tunatore@outlook.com");
		  messageMap2.put("key3", "topic spring framework");
		  messageMap2.put("key4", "topic udemy");
		  messageSender.sendTopic(messageMap2);
		  		  
		  return new ModelAndView("/admin/adminsecured");
		  
	  }
	  
	  @RolesAllowed(value={"ROLE_ADMIN"})
	  @RequestMapping(value="/sendJMSMessageCallback", method=RequestMethod.GET)
	  public ModelAndView sendJMSMessageCallback() {
		  
		  System.out.println("MessageController sendJMSMessageCallback is called with ADMIN ROLE");		  
		  messageSender.SendTopicWithCallBack();		  		 
		  return new ModelAndView("/admin/adminsecured");
		  
	  }
	  
	  @RolesAllowed(value={"ROLE_ADMIN"})
	  @RequestMapping(value="/getJMSMessage", method=RequestMethod.GET)
	  public ModelAndView getJMSMessage() {
		  System.out.println("Message Controller getJMSMessage synchronous " + messageReceiver.getJmsTemplate().receiveAndConvert("queue"));
		  System.out.println(messageReceiver.getJmsTemplate().receiveAndConvert("queue"));		  
		  return new ModelAndView("/admin/adminsecured");
	  }
}
