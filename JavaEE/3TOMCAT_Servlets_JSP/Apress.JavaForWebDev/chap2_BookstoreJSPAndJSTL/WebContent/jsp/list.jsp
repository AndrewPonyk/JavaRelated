<%@ page language="java" contentType="text/html; charset=ISO-8859-1"
	pageEncoding="ISO-8859-1"%>
<%@page import="java.util.List"%>
<%@page import="java.util.ArrayList"%>
<%@page import="java.util.Iterator"%>
<%@page import="com.apress.bookweb.model.Book"%>
<%@page import="com.apress.bookweb.model.Author"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<!DOCTYPE html >
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=ISO-8859-1">
</head>
<body>
	<table id="grid">
		<thead>
			<tr>
				<th id="th-title">Book Title</th>
				<th id="th-author">Author</th>
			</tr>
		</thead>


		<tbody>
			<c:forEach items="${bookList }" var="book">
				<tr>
					<th scope="row" id="r100">${book.bookTitle}</th>
					<c:set value="${book.id }" var="bookId" />
					<c:forEach items="${book.authors}" var="author">
						<c:if test="${bookId eq author.bookId }">
							<td>${author.firstName }${author.lastName}</td>
						</c:if>
					</c:forEach>
				</tr>
			</c:forEach>
	
		<!-- WITHOUT JSTL -->
			<%-- 			<%
        List<Book> books = (List<Book>)request.getAttribute("bookList");
        Iterator<Book> iterator = books.iterator();

        while (iterator.hasNext()) {
          Book book = (Book)iterator.next();
          Long bookId = book.getId();
          List<Author> authors = book.getAuthors();
         
        
  %>
			<tr>
				<th scope="row" id="r100"><%=book.getBookTitle()%></th>
				<% for (Author author: authors){
						 if ( book.getId().equals(author.getBookId())){

						  %><td><%=author.getFirstName()+"  " +author.getLastName()%></td>

				<% }}%>
			</tr>

			<%
          }
        
	
  %> --%>

		</tbody>

	</table>
</body>
</html>