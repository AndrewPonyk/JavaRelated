<!DOCTYPE html>

<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>    
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags"%>
<%@ taglib prefix="security" uri="http://www.springframework.org/security/tags" %>

<html>
	<head>
		<meta charset="utf-8">
		<title>Login</title>
	</head> 
	<body>
	<security:authorize ifAnyGranted="ROLE_USER,ROLE_ADMIN" var="isUSer"/>
	<font size="3" color="red"><b><c:if test="${not isUSer}">You are not logged in</c:if></b></font>
	<br/>
	<c:if test="${not empty param.error}">
    		<font size="3" color="red"><b>Either your username or password is wrong</b></font>
	</c:if>
	<form name="f" action="j_spring_security_check" method="post">
		<table>
			<tr>
				<td><spring:message code="email" text="Email"/>:</td>
				<td><input id="email" type="text" name="j_username" value="tunatore@gmail.com"/></td>
			</tr>
			<tr>
				<td><spring:message code="pass" text="Password"/>:</td>
				<td><input id="password" type="password" name="j_password" value="12345"/></td>
			</tr>
			<tr> 				   
				<td align="right" colspan="2"><input type="submit" value="Submit"></td> 					
			</tr>
		</table>
	</form>    
	<a href="javascript:history.back()">Back</a>
	</body>
</html>
