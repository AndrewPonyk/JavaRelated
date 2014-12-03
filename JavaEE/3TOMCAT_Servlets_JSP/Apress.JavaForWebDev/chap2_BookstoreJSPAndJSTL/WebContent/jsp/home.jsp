<%@ page language="java" contentType="text/html; charset=UTF-8"
	pageEncoding="UTF-8" isELIgnored="false"%>
	<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
	
<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
<title>Home page of Bookstore JSP</title>
<link rel="stylesheet" href="css/bookstore.css" type="text/css" />
<script src="js/bookstore.js" type="text/javascript"></script>
<script type="text/javascript" src="js/jquery-1.9.1.js"></script>
<script type="text/javascript">
	var gl = "global";
</script>
</head>
<body>
	<%
		String imageURL=application.getInitParameter("imageURL"); 
	%>
	<c:set value="${initParam.imageURL}" var="imageURL"/>
	<div id="centered">
		<jsp:include page="header.jsp" />
		<br />
		<jsp:include page="leftColumn.jsp" flush="true" />
		<span class="label">Featured Books</span>
		<table>
			<tr>
				<td><span class="tooltip_img1"><img
						src="${imageURL}/A9781430248064-small_3.png" /></span></td>
				<td><img src="${imageURL}/A9781430239963-small_1.png" /></td>
				<td><img src="${imageURL}/A9781430247647-small_3.png" /></td>
				<td><img src="${imageURL}/A9781430231684-small_8.png" /></td>
				<td><img src="${imageURL}/A9781430249474-small_1.png" /></td>
			</tr>
			<tr>
				<td><img src="${imageURL}/A9781430248187-small_1.png" /></td>
				<td><img src="${imageURL}/A9781430243779-small_2.png" /></td>
				<td><img src="${imageURL}/A9781430247401-small_1.png" /></td>
				<td><img src="${imageURL}/A9781430246596-small_1.png" /></td>
				<td><img src="${imageURL}/A9781430257349-small_1.png" /></td>
			</tr>
		</table>
	</div>
</body>
</html>