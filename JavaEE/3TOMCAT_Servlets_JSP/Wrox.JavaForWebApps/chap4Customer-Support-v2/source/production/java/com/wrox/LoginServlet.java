package com.wrox;

import java.io.IOException;
import java.util.Hashtable;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

@WebServlet(name = "loginServlet", urlPatterns = "/login")
public class LoginServlet extends HttpServlet {
	private static final Map<String, String> userDatabase = new Hashtable<>();
	static {
		userDatabase.put("Nicholas", "password");
		userDatabase.put("Sarah", "drowssap");
		userDatabase.put("Mike", "wordpass");
		userDatabase.put("John", "green");
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		HttpSession session = req.getSession();

		if (req.getParameter("logout") != null) {
			session.invalidate();
			resp.sendRedirect("login");
			return;
		}
		
		if (session.getAttribute("username") != null) {
			resp.sendRedirect("tickets");
			return;
		}

		req.setAttribute("loginFailed", false);
		req.getRequestDispatcher("/WEB-INF/jsp/view/login.jsp").forward(req,
				resp);
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {

		HttpSession session = req.getSession();
		if (session.getAttribute("username") != null) {
			resp.sendRedirect("tickets");
			return;
		}
		
		String username = req.getParameter("username");
		String password = req.getParameter("password");
		if (username == null || password == null
				|| !LoginServlet.userDatabase.containsKey(username)
				|| !password.equals(LoginServlet.userDatabase.get(username))) {
			req.setAttribute("loginFailed", true);
			req.getRequestDispatcher("/WEB-INF/jsp/view/login.jsp")
					.forward(req, resp);
		} else {
			session.setAttribute("username", username);
			//req.changeSessionId();  // servlet 3.1
			resp.sendRedirect("tickets");
		}
		
	}
}