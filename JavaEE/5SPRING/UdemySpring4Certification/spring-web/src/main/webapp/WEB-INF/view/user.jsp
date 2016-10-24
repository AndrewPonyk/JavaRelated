<!DOCTYPE html>

<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
    
<html>
	<head>
		<meta charset="utf-8">
		<title>User</title>
	</head> 
	<body>
		<h2>User</h2>
		<table>
			<tr><td><b>Iduser: </b></td><td>${userModelObject.idUser}</td></tr>
			<tr><td><b>Username: </b></td><td>${userModelObject.username}</td></tr>
			<tr><td><b>Password: </b></td><td>${userModelObject.password}</td></tr>
			<tr><td><b>Enabled: </b></td><td>${userModelObject.enabled}</td></tr>
		</table>
		<br/>
		<a href="javascript:history.back()">Back</a>
		
		<c:if test="${not empty users}">
			<h2>User List</h2>		
		</c:if>
		<table>
		<c:forEach var="user" items="${users}">
    		<tr><td><b>Iduser: </b></td><td><c:out value="${user.idUser}"/><br/></td></tr>
    		<tr><td><b>Username: </b></td><td><c:out value="${user.username}"/><br/></td></tr>
    		<tr><td><b>Password: </b></td><td><c:out value="${user.password}"/><br/></td></tr>
    		<tr><td><b>Enabled: </b></td><td>${user.enabled}</td></tr>
		</c:forEach>
		</table>
	</body>
</html>
