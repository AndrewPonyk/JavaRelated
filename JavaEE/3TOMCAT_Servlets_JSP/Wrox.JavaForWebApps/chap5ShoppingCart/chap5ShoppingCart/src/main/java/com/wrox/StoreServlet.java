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

@WebServlet(name = "storeServlet", urlPatterns = "/shop")
public class StoreServlet extends HttpServlet {

	private final Map<Integer, String> products = new Hashtable<Integer, String>();

	public StoreServlet() {
		this.products.put(1, "Sandpaper");
		this.products.put(2, "Nails");
		this.products.put(3, "Glue");
		this.products.put(4, "Paint");
		this.products.put(5, "Tape");
	}

	@Override
	protected void doGet(HttpServletRequest request,
			HttpServletResponse response) throws ServletException, IOException {

		String action = request.getParameter("action");
		if (action == null) {
			action = "browse";
		}

		switch (action) {
		case "addToCart":
			this.addToCart(request, response);
			break;
		case "viewCart":
			this.viewCart(request, response);
			break;
		case "browse":
		default:
			this.browse(request, response);
			break;
		}
	}
	
	private void browse(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		request.setAttribute("products", this.products);
		request.getRequestDispatcher("/WEB-INF/jsp/view/browse.jsp").forward(
				request, response);
	}

	private void viewCart(HttpServletRequest request,
			HttpServletResponse response) throws ServletException, IOException {
		request.setAttribute("products", this.products);
		request.getRequestDispatcher("/WEB-INF/jsp/view/viewCart.jsp").forward(
				request, response);
	}

	private void addToCart(HttpServletRequest request,
			HttpServletResponse response) throws ServletException, IOException {
		int productId;
		try {
			productId = Integer.parseInt(request.getParameter("productId"));
		} catch (Exception e) {
			response.sendRedirect("shop");
			return;
		}
		HttpSession session = request.getSession();
		if (session.getAttribute("cart") == null)
			session.setAttribute("cart", new Hashtable<Integer, Integer>());
		@SuppressWarnings("unchecked")
		Map<Integer, Integer> cart = (Map<Integer, Integer>) session
				.getAttribute("cart");
		if (!cart.containsKey(productId)){
			cart.put(productId, 1);
		}else{
			cart.put(productId, cart.get(productId) + 1);
		}
		
		this.viewCart(request, response);
		//response.sendRedirect("shop?action=viewCart"); // from book , doesnt work then user 
		//disable cookies,  (if user disable cookies every url must include JSESSIONID)
		
		
		//response.sendRedirect("shop;JSESSIONID_custom=" + session.getId() + "?action=viewCart");
	}
}
