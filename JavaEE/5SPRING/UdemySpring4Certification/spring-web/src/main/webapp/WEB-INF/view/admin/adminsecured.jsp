<!DOCTYPE html>

<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>    
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags"%>
<%@ taglib prefix="security" uri="http://www.springframework.org/security/tags" %>

<html>
	<head>
		<meta charset="utf-8">
		<title>Admin Secured</title>
	</head> 
	<body>
	<c:url value="/getUserById" var="adminGetUserByIdURL" />
	<c:url value="/getUserByIdPathVariable" var="adminGetUserByIdPathVariableURL" />
	<c:url value="/getAllUsers" var="adminQueryAllUsersURL" />
	<c:url value="/testUserSessionAttribute" var="adminTestUserSessionAttribute" />
	<h1><b>Admin With Secured</b></h1>
	<security:authorize ifAnyGranted="ROLE_USER,ROLE_ADMIN">
			<b>You are logged in as: </b><security:authentication property="principal.username"/> with the role of: <b><security:authentication property="principal.authorities"/></b><br/>
			<span style="color: #568C00;"><security:authentication property="principal.username"/></span>
			<a style="color: #568C00!important;" href="<c:url value="/j_spring_security_logout"/>">Logout</a>
	</security:authorize> 	
	<br/>
	<br/>
	<b>@RequestParam Example</b>
	<form action="${adminGetUserByIdURL}" method="post">
		<table>
			<tr><td><b>IdUser:</b></td><td><input type="text" name="IDUSER"/></td></tr>
			<tr><td colspan="2" align="right"><input type="submit" value="GET USER"></td></tr>
		</table>
	</form>		
	<br/>
	<b>@PathVariable Example</b>
	<br/>
	<a href="${adminGetUserByIdPathVariableURL}/IDUSER/2">${adminGetUserByIdPathVariableURL}/IDUSER/2</a>
	<br/>
	<br/>
	<b>getAllUsers Model Example</b>
	<br/>
	<a href="${adminQueryAllUsersURL}">${adminQueryAllUsersURL}/getAllUsers</a>	
	<br/>	
	<br/>	
	<b>getAllUsers Model Admin Role Granted?</b>
	<br/>	
	<br/>	
	<security:authorize access="hasRole('ROLE_ADMIN')" var="isAdmin" />	
	<font size="3" color="red"><b><c:if test="${not isAdmin}">You don't have admin rights</c:if></b></font>
	<font size="3" style="color: #568C00!important;"><b><c:if test="${isAdmin}">You have admin rights</c:if></b></font> 	
	<br/>	
	<security:authorize ifAnyGranted="ROLE_ADMIN">
	<br/>	
	<b>getAllUsers Model Admin Role Granted</b>
	<br/>
	<a href="${adminQueryAllUsersURL}">${adminQueryAllUsersURL}/getAllUsers</a>	
	<br/>
	</security:authorize>
	<br/>
	<a href="${adminTestUserSessionAttribute}/IDUSER/4">${adminTestUserSessionAttribute}/testUserSessionAttribute/IDUSER/4</a>
	<br/>
	<br/>	
	<a href="${pageContext.request.contextPath}">Index</a>	
	<a href="javascript:history.back()">Back</a>		
	</body>
</html>
