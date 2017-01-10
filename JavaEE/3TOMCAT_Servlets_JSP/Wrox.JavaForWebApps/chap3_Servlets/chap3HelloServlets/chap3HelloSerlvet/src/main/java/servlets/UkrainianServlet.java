package servlets;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@WebServlet(urlPatterns = "/ukr")
public class UkrainianServlet extends HttpServlet{
	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		
		getServletContext().setAttribute("ua", "Variable from Application scope");
		request.setAttribute("ukrainianArgument", "Український текст їїї");
		request.getRequestDispatcher("/ukr.jsp").forward(request, response);
	}
}
