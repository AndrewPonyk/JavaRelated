<!DOCTYPE html>

<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>    
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags"%>

<html>
	<head>
		<meta charset="utf-8">
		<title>Register</title>
	</head> 
	<body>
	<c:url value="/registeruser" var="registerControllerURL" />
	<h1><b>Register</b></h1>	
	<form action="${registerControllerURL}" method="post">
		<table>
			<tr><td><b>email:</b></td><td><input type="text" name="email"/></td></tr>
			<tr><td><b>password:</b></td><td><input type="password" name="password"/></td></tr>
			<tr><td colspan="2" align="right"><input type="submit" value="Register"></td></tr>
		</table>
	</form>	
	<br/>	
	<a href="javascript:history.back()">Back</a>		
	</body>
</html>
