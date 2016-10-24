package spring.controller;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.AbstractController;

public class XMLController extends AbstractController {

	@Override
	protected ModelAndView handleRequestInternal(HttpServletRequest request,
			HttpServletResponse response) throws Exception {
		
		System.out.println("XMLController is called");
		System.out.println("XMLController request: " + request.getParameter("test"));		
		return new ModelAndView("xml","xmlModelObject", request.getParameter("test"));
		
	}

	
}
