package spring.controller;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

import spring.bean.RegisterService;
import spring.bean.User;


//Controller is a component so it has to be scanned with
// <context:component-scan base-package="spring.controller" />
@Controller
public class RegisterController {
	
	  @Autowired
	  private RegisterService registerService;
	  
	  @RequestMapping(value="/registeruser", method=RequestMethod.POST)
	  public ModelAndView register(
			  @RequestParam(required=false,defaultValue="") String email,
			  @RequestParam(required=false,defaultValue="") String password,
			  HttpServletRequest request, HttpServletResponse response)
	    throws Exception
	  {
		  
	    System.out.println("RegisterController register is called");
	    System.out.println("RegisterController register email: "  + email);
	    System.out.println("RegisterController register email: "  + password);
    
	    User user = new User();
	    user.setUsername(email);
	    user.setPassword(password);
	    
	    boolean result = registerService.registerUser(user);
	    
	    if (result)
	    return new ModelAndView("success","message", "Successful registration");
	    else
	    return new ModelAndView("error","message", "Error while registration");
	    
	  }
	 

}
