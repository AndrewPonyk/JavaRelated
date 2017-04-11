<%@ page language="java" contentType="text/html; charset=ISO-8859-1"
         pageEncoding="ISO-8859-1"%>

<%@ taglib prefix="form" uri="http://www.springframework.org/tags/form" %>
<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<html>
<head>
    <title>Home.jsp</title>
</head>
<body>
<h2>${greetings}</h2>
<div style="border:1px solid black">
    List displayed by JSTL

    <c:forEach var="item" items="${listData}">
        <h3>${item}</h3>
    </c:forEach>
</div>
</body>
</html>
