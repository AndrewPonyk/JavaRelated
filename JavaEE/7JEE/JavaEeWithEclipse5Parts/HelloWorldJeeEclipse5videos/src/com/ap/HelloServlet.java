package com.ap;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

import javax.ejb.EJB;
import javax.persistence.EntityManagerFactory;
import javax.persistence.PersistenceUnit;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.ap.model.Myuser;

/**
 * Servlet implementation class HelloServlet
 */
@WebServlet("/HelloServlet")
public class HelloServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;
    @EJB
    private HelloBean bean;
	
    @PersistenceUnit
    EntityManagerFactory emf;
    
    public HelloServlet() {
        super(); 
    }
   
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		System.out.println("Logging");
		
		response.setContentType("text/html");// with this our html tags (like <br>) works!!
		String servedInfo = "Served at::: " + request.getContextPath();    
		final PrintWriter writer = response.getWriter();
		writer.print(servedInfo); 
		writer.print("<br/>");
		writer.print(bean.sayHello("Rob"));
		writer.print("<br/><br/>");
		writer.print("Users from db <table style='border-collapse: collapse;'>");
		
		List<Myuser> users = (List<Myuser>)emf.createEntityManager().createNamedQuery("Myuser.findAll")
				.getResultList();
		users.forEach(user-> {
			writer.print("<tr>");
			writer.print("<td style='border:1px solid black'>" + user.getName() + "</td>");
			writer.print("<td style='border:1px solid black'>" + user.getPassword() + "</td>");
		});
		writer.print("</table>");
		
	}

	/**
	 * @see HttpServlet#doPost(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		// TODO Auto-generated method stub 
		doGet(request, response);
	}

}
