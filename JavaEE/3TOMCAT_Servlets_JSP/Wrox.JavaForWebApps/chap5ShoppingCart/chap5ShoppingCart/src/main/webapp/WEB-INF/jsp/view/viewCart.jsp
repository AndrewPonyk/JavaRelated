<%@ page import="java.util.Map"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<!DOCTYPE html>
<html>
<head>
<title>View Cart</title>
</head>
<body>
	<h2>View Cart</h2>
	<a href="<c:url value="/shop" />">Product List</a>
	<br />
	<br />
	<%
		@SuppressWarnings("unchecked")
		Map<Integer, String> products = (Map<Integer, String>) request
				.getAttribute("products");
		@SuppressWarnings("unchecked")
		Map<Integer, Integer> cart = (Map<Integer, Integer>) session
				.getAttribute("cart");
		if (cart == null || cart.size() == 0)
			out.println("Your cart is empty.");
		else {
			for (int id : cart.keySet()) {
				out.println(products.get(id) + " (qty: " + cart.get(id)
						+ ")<br />");
			}
		}
	%>
</body>
</html>