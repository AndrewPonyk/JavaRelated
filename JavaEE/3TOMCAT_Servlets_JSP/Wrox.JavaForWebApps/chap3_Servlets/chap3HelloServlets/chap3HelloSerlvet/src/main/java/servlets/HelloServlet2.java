package servlets;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@WebServlet(urlPatterns = "*.do", loadOnStartup = 1)
public class HelloServlet2 extends HttpServlet{
	
	private static final long serialVersionUID = 1L;

	@Override
	public void init() throws ServletException {
		System.out.println("Servlet " + getServletName() + " created");
	}
	
	@Override
	public void destroy() {
		System.out.println("Servlet " + getServletName() + " destroyed");
	}
	
	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		super.doPost(req, resp);
	}
	
	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		resp.getWriter().println("2 servlet; Maven web projects 1");
		
	}
}
