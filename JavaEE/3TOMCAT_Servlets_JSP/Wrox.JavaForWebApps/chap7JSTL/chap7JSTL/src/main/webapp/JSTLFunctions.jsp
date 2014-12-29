<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8" isELIgnored="false"%>
    <%@taglib uri="http://java.sun.com/jsp/jstl/functions"  prefix="fn"%>
    <%@taglib uri="http://java.sun.com/jsp/jstl/core"  prefix="c"%>
<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
<title>JSTL Functions</title>
<style type="text/css">
    div{
        border-top: 1px solid black;
    }
</style>
</head>
<body>
    <b>http://docs.oracle.com/javaee/5/jstl/1.1/docs/tlddocs/fn/tld-summary.html</b>
    <hr>
    <h3>Example of using JSTL functions</h3>

	<div>indexOf: ${fn:indexOf("hello world","world") }</div>
	
	<div>
	split:${fn:split("some,comma,separated,values", ",")}
	   <br>
	   <c:forEach items="${ fn:split('some,comma,separated,values', ',')}" var="item">
	       <b>${item};</b>
	   </c:forEach>
	</div>
	
	<div>
	   endsWith:${fn:endsWith("hello", "ao") }
	</div>
</body>
</html>