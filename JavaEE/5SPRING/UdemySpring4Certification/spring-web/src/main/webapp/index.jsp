<!DOCTYPE html>

<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
    <%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags"%>
<%@ taglib prefix="security" uri="http://www.springframework.org/security/tags" %>

<html>
	<head>
		<meta charset="utf-8">
		<title>Welcome</title>
	</head> 
	<body>
		<c:url value="/login.html" var="loginURL" />
		<c:url value="/register.html" var="registerURL" />
		<c:url value="/xml.html" var="xmlURL" />
		<c:url value="/admin.html" var="adminURL" />
		<c:url value="/admin/adminsecured.html" var="adminURLSecured" />
		<c:url value="/adminMethodJSR" var="adminMethodJSR" />
		<c:url value="/adminMethodSecuredSpEL" var="adminMethodSecuredSpEL" />
		<c:url value="/sendJMSMessage" var="sendJMSMessage" />
		<c:url value="/sendJMSMessageAsync" var="sendJMSMessageAsync" />
		<c:url value="/sendJMSMessageCallback" var="sendJMSMessageCallback" />
		<c:url value="/getJMSMessage" var="getJMSMessage" />
		<c:url value="/admin/rest.html" var="rest" />
		<h1>Index</h1>	
		
		<hr/>
			
		<security:authorize ifAnyGranted="ROLE_USER,ROLE_ADMIN">
			<b>You are logined as: </b><security:authentication property="principal.username"/> with the role of: <b><security:authentication property="principal.authorities"/></b><br/>
			<span style="color: #568C00;"><security:authentication property="principal.username"/></span>
			<a style="color: #568C00!important;" href="<c:url value="/j_spring_security_logout"/>">Logout</a>
		</security:authorize> 	
			
		<h2>Spring MVC</h2>
		<a href="${loginURL}">Login</a><br/>
		<a href="${registerURL}">Register</a><br/>
		<a href="${xmlURL}">XMLControllerExample</a><br/>
		
		<h2>Spring Security</h2>
		<a href="${adminURL}">Admin without secured</a> <br/>
		<a href="${adminURLSecured}">AdminURLSecured</a><br/>
		<a href="${adminMethodJSR}">Admin adminMethodJSR</a><br/>
		<a href="${adminMethodSecured}">Admin adminMethodSecured</a><br/>
		<a href="${adminMethodSecuredSpEL}">Admin adminMethodSecuredSpEL SpEL</a><br/>
		
		<h2>Spring JMS</h2>
		<a href="${sendJMSMessage}">Admin sendJMSMessage synchronous queue</a><br/>
		<a href="${getJMSMessage}">Admin getJMSMessage synchronous queue</a><br/>
		<a href="${sendJMSMessageAsync}">Admin sendJMSMessage asynchronous queue</a><br/>
		<a href="${sendJMSMessageCallback}">Admin sendJMSMessageCallback asynchronous queue</a><br/>
		
		<h2>REST</h2>
		<a href="${rest}">Admin REST examples</a><br/>
		
	</body>
</html>
