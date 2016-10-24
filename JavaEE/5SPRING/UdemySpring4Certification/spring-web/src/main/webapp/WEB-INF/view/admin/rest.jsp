<!DOCTYPE html>

<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>    
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags"%>
<%@ taglib prefix="security" uri="http://www.springframework.org/security/tags" %>

<html>
	<head>
		<meta charset="utf-8">
		<title>Admin REST calls</title>
	</head> 
	<body>	
	<c:url value="/echo/person1/tunatore/person2/testuser" var="restControllerEchoExample" />
	<c:url value="/md5/text/tunatore" var="adminRestControllermd5" />
	<c:url value="/responseStatusCode" var="restControllerHtpStatus" />
	<c:url value="/getAllUsersJSON" var="adminRestControllerGetAllUsersJSON" />
	<c:url value="/getAllDBLogsXML" var="adminRestControllerGetAllDBLogsXML" />
	<h1><b>Admin REST calls</b></h1>
	<security:authorize ifAnyGranted="ROLE_ADMIN">
			<b>You are logined as: </b><security:authentication property="principal.username"/> with the role of: <b><security:authentication property="principal.authorities"/></b><br/>
			<span style="color: #568C00;"><security:authentication property="principal.username"/></span>
			<a style="color: #568C00!important;" href="<c:url value="/j_spring_security_logout"/>">Logout</a>
		</security:authorize> 		
		<br/>
		<br/>
		<a href="${restControllerEchoExample}">@RestControllerExample echo</a><br/>
		calling with the value : <b>${restControllerEchoExample}</b>
		<br/>
		<br/>
		<a href="${adminRestControllermd5}">@AdminControllerExample md5String</a><br/>
		calling with the value : <b>${adminRestControllermd5}</b>
		<br/>
		<br/>
		<a href="${restControllerHtpStatus}">@AdminControllerExample responseStatusTest</a><br/>
		calling with the value : <b>${restControllerHtpStatus}</b>
		<br/>
		<br/>
		<a href="${adminRestControllerGetAllUsersJSON}">@AdminControllerExample getAllUsersJSON</a><br/>
		calling with the value : <b>${adminRestControllerGetAllUsersJSON}</b>
		<br/>
		<br/>
		<a href="${adminRestControllerGetAllDBLogsXML}">@AdminControllerExample getAllDBLogsXML</a><br/>
		calling with the value : <b>${adminRestControllerGetAllDBLogsXML}</b>
		<br/>
		<br/>
		<a href="javascript:history.back()">Back</a>		
	</body>
</html>
