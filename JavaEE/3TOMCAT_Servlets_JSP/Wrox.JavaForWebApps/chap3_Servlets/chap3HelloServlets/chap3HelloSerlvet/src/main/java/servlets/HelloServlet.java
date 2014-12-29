package servlets;

import java.io.IOException;
import java.util.Enumeration;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@WebServlet(urlPatterns = "/hello", loadOnStartup = 1)
public class HelloServlet extends HttpServlet{
	
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
		/// WITH THIS Ukrainian letters works
		resp.setHeader("Content-Type", "text/html; charset=UTF-8");
		resp.getWriter().println("Maven web projects 1 <br>");

		// Display all parameters from request
		Map<String, String[]> parameterMap = req.getParameterMap();

		for (String item : parameterMap.keySet()) {
			resp.getWriter().println(
					item + " = " + parameterMap.get(item)[0] + "<br>");
		}

		// / Display all request headers
		resp.getWriter().println("\nRequest Headers <br>");
		Enumeration<String> headers = req.getHeaderNames();
		while (headers.hasMoreElements()) {
			String header = headers.nextElement();
			resp.getWriter().println(header + " = " + req.getHeader(header) + "<br>");
		}
		resp.getWriter().println("hello<br>");
		// / Display Ukrainian letters
		///resp.setStatus(404);
		resp.getWriter().println("Привіт");
	}

}