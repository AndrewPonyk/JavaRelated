<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<html>
<head>
    <title>Registration Confirmation Page</title>
</head>
<body>
<p>Success!!!</p>
<p> message : ${success}
</p>

Go back to <a href="<c:url value='/list' />">List of All Employees</a>
</body>
</html>
