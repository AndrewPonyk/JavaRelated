package apress.helloworld;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class HelloWorld extends HttpServlet {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {

		resp.setContentType("text/html");
		PrintWriter printWriter = resp.getWriter();
		printWriter.println("<h2>");
		printWriter.println("Hello World!");
		printWriter.println("</h2>");
		
		// Getting init and context parameters
		String helloWorldInitParam = getServletConfig().getInitParameter("helloWorldParam");
		String servletContextParam = getServletContext().getInitParameter("email");
		
		printWriter.println("ServletContext parameter with key 'email' : " + servletContextParam + "<br>");
		printWriter.println("ServletInit parameter with key 'helloWorldParam' : " + helloWorldInitParam);
	}
}
