<%@ page language="java" contentType="text/html; charset=ISO-8859-1"
	pageEncoding="ISO-8859-1"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<%@page import="java.util.List"%>
<%@page import="java.util.ArrayList"%>
<%@page import="java.util.Iterator"%>
<%@page import="com.apress.bookweb.model.Book"%>
<%@page import="com.apress.bookweb.model.Author"%>

<!DOCTYPE html >
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=utf-8" />
<meta name="viewport" content="width=device-width, initial-scale=1" />
<script src="js/jquery-1.9.1.js"></script>
<link rel="stylesheet" href="css/bookstore.css" type="text/css" />
<script src="js/bookstore.js"></script>


</head>
<body>
	<div id="centered">
		<jsp:include page="header.jsp" flush="true" />
		<br />
		<jsp:include page="leftColumn.jsp" flush="true" />
		<%-- <%
			String category = request.getParameter("category");
			String categoryId = request.getParameter("categoryId");
			if (category != null) {
		%> --%>

		<c:set scope="request" var="category" value="${param.category}" />
		<c:set scope="request" var="categoryId" value="${param.categoryId}" />

		<div>
			<span class="label" style="margin-left: 15px;">List of
				${category} Books </span>
		</div>

		<%-- <%
			}
		%> --%>
		<table id="grid">
			<thead>
				<tr>
					<th id="th-title">Book Title</th>
					<th id="th-author">Author</th>
				</tr>
			</thead>
			<tbody>
				<!-- WITHOUT JSTL -->
				<%-- <%
					List<Book> books = (List<Book>) request.getAttribute("bookList");
					Iterator<Book> iterator = books.iterator();
					while (iterator.hasNext()) {
						Book book = (Book) iterator.next();
						if (book.getCategoryId().toString().equals(categoryId)) {
							Long bookId = book.getId();
							List<Author> authors = book.getAuthors();
				%>
				<tr>
					<th scope="row" id="r100"><%=book.getBookTitle()%></th>
					<%
						for (Author author : authors) {
									if (book.getId().equals(author.getBookId())) {
					%><td><%=author.getFirstName() + "  "
									+ author.getLastName()%></td>

					<%
						}
								}
					%>

				</tr>

				<%
					}
					}
				%> --%>
				
				<!-- JSTL !!!!!!!!! -->
				<c:forEach items="${bookList}" var="book">
					<c:if test="${book.getCategoryId() eq categoryId}">
						<c:set var="bookId" value="${ book.getId()}" />
						<c:set var="authors" value="${book.getAuthors() }" />
						<tr>
							<th scope="row" id="r100">${book.bookTitle}</th>
							<c:forEach var="author" items="${book.authors}">
								<c:if test="${author.bookId eq book.id }">
									<td>${author.firstName}${ author.lastName}</td>
								</c:if>
							</c:forEach>
						</tr>
					</c:if>
				</c:forEach>

			</tbody>

		</table>
	</div>
</body>
</html>