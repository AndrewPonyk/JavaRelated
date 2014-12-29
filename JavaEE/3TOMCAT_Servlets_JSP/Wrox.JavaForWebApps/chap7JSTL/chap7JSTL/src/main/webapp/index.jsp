<%@page language="java" isELIgnored="false" autoFlush="true"%>
<%@taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<%@taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn"%>
<html>
<body>
	<h2>Hello World!</h2>
	
	
	<c:out value="Hello from JSTL"></c:out>
    <c:if test="${10>3 }">10>3</c:if>
    
    <hr>
    <b>Using http://java.sun.com/jsp/jstl/functions</b>
    <div>
        ${fn:substring("Hello", 1, 3)}
    </div>
    
    <hr>
    forTokens:
    <c:forTokens items="1,2,3,4,5,6" delims="," var="item">
        ${item}===
    </c:forTokens>
</body>
</html>
