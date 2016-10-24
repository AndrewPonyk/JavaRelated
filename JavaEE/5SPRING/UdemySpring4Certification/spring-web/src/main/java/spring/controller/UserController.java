package spring.controller;

import java.util.List;

import javax.annotation.security.RolesAllowed;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

import spring.bean.User;
import spring.bean.UserManager;



@Controller
public class UserController {

	  @Autowired
	  private UserManager userManager;
	  
	  private User user;
	  
	  @RequestMapping(value="/getUserById", method=RequestMethod.POST)
	  public ModelAndView getUserByID(
			  @RequestParam(value="IDUSER") int idUser,
			  HttpServletRequest request, HttpServletResponse response)
	    throws Exception
	  {
		  System.out.println("UserController getUserById is called");
		  user = null;
		  try {
			    user = userManager.queryUserWithInternalRowMapper(idUser);
		  } catch (Exception e) {
			  	e.printStackTrace();			  	
		  }		 
		  if (user != null)
			  return new ModelAndView("user","userModelObject",user);
		  else
			  return new ModelAndView("error","message", "Error while getting UserModelObject");
	  }
	  @RequestMapping(value="/getUserByIdPathVariable/IDUSER/{idUser}", method=RequestMethod.GET)
	  public ModelAndView getUserByIDPathVariable(
			  @PathVariable(value="idUser") int idUser,
			  HttpServletRequest request, HttpServletResponse response)
	    throws Exception
	  {
		  System.out.println("UserController getUserByIDPathVariable is called");
		  System.out.println("UserController getUserByIDPathVariable idUser: " + idUser);
		  user = null;
		  try {
			    user = userManager.queryUserWithInternalRowMapper(idUser);
		  } catch (Exception e) {
			  	e.printStackTrace();			  	
		  }			  
		  if (user != null)
		  return new ModelAndView("user","userModelObject",user);
		  else
		  return new ModelAndView("error","message", "Error while getting UserModelObject");
	  }
	  
	  @RequestMapping(value="/getAllUsers", method=RequestMethod.GET)
	  public String queryAllUsers(Model model)
	    throws Exception
	  {
		  System.out.println("UserController queryAllUsers is called");		
		  
		  List<User> users = null;
		  try {
			   users = userManager.queryUserWithResultSetExtractor();
		  } catch (Exception e) {
			  	e.printStackTrace();			  	
		  }			  
		  model.addAttribute("users",users);		  
		  
		  if (users != null)
		  return "user";
		  else
		  return "error";
	  }
	  	  
	  @RequestMapping(value="/testUserSessionAttribute/IDUSER/{idUser}", method=RequestMethod.GET)
	  public ModelAndView testUserSessionAttribute(
			  @PathVariable(value="idUser") int idUser, HttpSession session)
	    throws Exception
	  {
		  System.out.println("UserController testUserSessionAttribute is called");
		  System.out.println("UserController testUserSessionAttribute sessionObject: " + session.getAttribute("sessionObject"));
		  user = null;
		  try {
			    user = userManager.queryUserWithInternalRowMapper(idUser);
			    session.setAttribute("sessionObject", user.getIdUser() + " - " + user.getUsername());
		  } catch (Exception e) {
			  	e.printStackTrace();			  	
		  }		 
		  if (user != null)
			  return new ModelAndView("user","userModelObject",user);
		  else
			  return new ModelAndView("error","message", "Error while getting UserModelObject");
	  }

	  @RolesAllowed(value={"ROLE_ADMIN"})
	  @RequestMapping(value="/adminMethodJSR", method=RequestMethod.GET)
	  public ModelAndView adminMethodJSR() {
		  System.out.println("UserController adminMethodJSR is called with ADMIN ROLE");
		  return new ModelAndView("/admin/adminsecured");
		  
	  }
	  //SpEL usage at method level security
	  @PreAuthorize("hasRole('ROLE_ADMIN')")
	  @RequestMapping(value="/adminMethodSecuredSpEL", method=RequestMethod.GET)
	  public ModelAndView preAuthorize() {
		  System.out.println("UserController preAuthorize is called for ROLE_ADMIN");
		  return new ModelAndView("/admin/adminsecured");
	  }
	  
	  @Secured(value={"ROLE_ADMIN"})
	  @RequestMapping(value="/adminMethodSecured", method=RequestMethod.GET)
	  public ModelAndView adminMethodSecured() {
		  System.out.println("UserController adminMethodSecured is called with ADMIN ROLE");
		  return new ModelAndView("/admin/adminsecured");
		  
	  }
}
