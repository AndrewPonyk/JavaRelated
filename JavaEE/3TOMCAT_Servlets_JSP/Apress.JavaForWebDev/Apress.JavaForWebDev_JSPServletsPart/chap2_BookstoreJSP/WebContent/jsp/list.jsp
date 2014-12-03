<%@ page language="java" contentType="text/html; charset=ISO-8859-1"
	pageEncoding="ISO-8859-1"%>
<%@page import="java.util.List"%>
<%@page import="java.util.ArrayList"%>
<%@page import="java.util.Iterator"%>
<%@page import="com.apress.bookweb.model.Book"%>
<%@page import="com.apress.bookweb.model.Author"%>

<!DOCTYPE html >
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=ISO-8859-1">
</head>
<body>
	<%
			String category = request.getParameter("category");
		if(category != null){
		%>
	<div>
		<span class="label" style="margin-left: 15px;"> List of <%= category%>
			Books
		</span>
	</div>

	<%} %>
	<table id="grid">
		<thead>
			<tr>
				<th id="th-title">Book Title</th>
				<th id="th-author">Author</th>
			</tr>
		</thead>


		<tbody>
			<%
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
        
	
  %>

		</tbody>

	</table>
</body>
</html>