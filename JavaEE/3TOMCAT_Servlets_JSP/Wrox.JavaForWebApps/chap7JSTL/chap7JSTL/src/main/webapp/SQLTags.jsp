<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8" isELIgnored="false"%>
<%@taglib uri="http://java.sun.com/jsp/jstl/sql" prefix="sql" %>
<%@taglib uri="http://java.sun.com/jsp/jstl/core"  prefix="c"%>
<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html>
	<head>
	<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
	   <title>Using sql tags</title>
	</head>
	<body>

    <sql:query var="AP_USERS" dataSource="jdbc/andrewdb">
        select COLUMN1 from AP_USERS
    </sql:query>

    ${AP_USERS }

    <div>Table AP_USERS : </div>
    <c:forEach items="${ AP_USERS.rows}" var="item">
            <p>${item.COLUMN1 }</p>
    </c:forEach>


	</body>
</html>