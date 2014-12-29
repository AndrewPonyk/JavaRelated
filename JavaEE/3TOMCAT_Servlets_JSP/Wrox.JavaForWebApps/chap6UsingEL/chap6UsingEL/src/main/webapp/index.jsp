<%@ page language="java" isELIgnored="false" %>
<%@page import="mainpack.User"%>

<html>
	<head>
	   <title>Using EL</title>
	</head>
	<body>
	<h2>Hello World!</h2>

	${user.toString()}
	
	
	<!-- !!!!!!!!!!!!!!!!! -->
	<!-- We can't use scrptlets variables inside EL  -->
	
	<!-- Wrong , you can get only properties of object -->
    <%= ((User)request.getAttribute("user")).publicField%>
	
	<!-- Correct you can call methods -->
	${user.getPrivateProperty()}
	
	<!-- Correct, behind the scene user.getPrivateProperty() is called -->
	${user.privateProperty }
	
	<!-- Works -->
	${user.voidMethod() }
	
	<!-- Works only in EL 3.0 (3.0 is JEE 7)  -->
	${mainpack.User.STATIC_CONSTANT}
	${java.lang.Integer.MAX_VALUE}
	
	<!-- Concatenate Strings works from EL 3.0-->
	<%-- ${"1"+="2"} --%>
	
	
	<hr>
	Available cookies :
	${cookie }
	
	<hr>
	Available parameters :
	${param }
	
	<hr>
	The final operator that the EL engine evaluates is the semicolon (;) operator, also a new feature in
EL 3.0. This operator mimics the comma (,) operator in C, allowing the specification of several
expressions with the values of all but the last expression being discarded. To understand this, refer
to the following expression.
\${x = y + 3; object.callMethod(x); 'Hello, World!'}
	The string literal "Hello, World!" is evaluated. The result of this expression is the result of
the expression after the last semicolon only: "Hello, World!"
	</body>
</html>