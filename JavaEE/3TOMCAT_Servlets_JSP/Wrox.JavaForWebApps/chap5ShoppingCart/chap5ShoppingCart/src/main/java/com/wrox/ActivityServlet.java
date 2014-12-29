package com.wrox;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Vector;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

@WebServlet(name = "activitySerlvet", urlPatterns = "/do/*")
public class ActivityServlet extends HttpServlet {

	@Override
	protected void doGet(HttpServletRequest request,
			HttpServletResponse response) throws ServletException, IOException {

		this.recordSessionActivity(request);
		this.viewSessionActivity(request, response);

	}

	private void recordSessionActivity(HttpServletRequest request) {

		HttpSession session = request.getSession();

		Vector<PageVisit> visits;
		if (session.getAttribute("activity") == null) {
			visits = new Vector<PageVisit>();
			session.setAttribute("activity", visits);
		} else {
			visits = (Vector<PageVisit>) session.getAttribute("activity");
		}

		PageVisit now = new PageVisit();
		now.enteredTimestamp = (System.currentTimeMillis());
		if (request.getQueryString() == null)
			now.request = (request.getRequestURL().toString());
		else
			now.request = (request.getRequestURL() + "?" + request
					.getQueryString());

		try {
			now.ipAddress = (InetAddress.getByName(request.getRemoteAddr()));
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
		visits.add(now);
	}

	private void viewSessionActivity(HttpServletRequest request,
			HttpServletResponse response) throws ServletException, IOException {
		request.getRequestDispatcher(
				"/WEB-INF/jsp/view/viewSessionActivity.jsp").forward(request,
				response);
	}
}
