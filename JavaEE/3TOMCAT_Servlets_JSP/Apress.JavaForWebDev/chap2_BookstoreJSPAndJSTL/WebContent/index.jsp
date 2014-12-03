<%@page import="java.util.Map"%>
<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8" isELIgnored="false"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
<title>Insert title here</title>
<style type="text/css">
	body {
		background-color: #0F0;
	}
</style>
</head>
<body>

	To see Bookstore application go 
	<a href="<c:url value="/books"/>">here</a>
	
	<hr>
	  
	  <!-- Getting context param from web.xml -->
	${pageContext.servletContext.getInitParameter("contextParamInWebXML")}
	<%=application.getInitParameter("contextParamInWebXML") %>
	<hr>
	${initParam.imageURL}
	<hr>
	<div style="font-size: 25px;">
	Some JSTL stuff :
	</div>
	
	<c:if test="${10>4}">
		10 > 4
	</c:if>
	
	<br>
	<c:choose>
		<c:when test="${10>40}">10 >40</c:when>
		<c:otherwise>10 is not > 40</c:otherwise>
	</c:choose>
	
	<div style="border: 1px solid black;margin: 15px;">
		<div style="border: 1px solid black">
			Task display el expression as string
		</div>
		Solution (inspect element and see source of jsp):${'${header.connection}'} 
	</div>
	
	<div style="border: 1px solid red; margin:15px;">
		Operators in EL:

		<table border="1">
			<tr>
				<td><b>Relational Operator</b></td>
				<td><b>Boolean Result</b></td>
			</tr>
			<tr>
				<td>${'${'}10&lt; 20}</td>
				<td>${10 < 20}</td>
			</tr>
			<tr>
				<td>${'${'}10&gt; 20}</td>
				<td>${10 > 20}</td>
			</tr>
			<tr>
				<td>${'${'}10&gt;= 10}</td>
				<td>${10 >= 10}</td>
			</tr>
			<tr>
				<td>${'${'}10&lt;= 10}</td>
				<td>${10 <= 10}</td>
			</tr>
			<tr>
				<td>${'${'}10== 10}</td>
				<td>${10 == 10}</td>
			</tr>
			<tr>
				<td>${'${'}10!= 20}</td>
				<td>${10 != 20}</td>
			</tr>
			<tr>
			<tr>
				<td>${'${'}10lt 20}</td>
				<td>${10 lt 20}</td>
			</tr>
			<tr>
				<td>${'${'}10gt 20}</td>
				<td>${10 gt 20}</td>
			</tr>
			<tr>
				<td>${'${'}10le 10}</td>
				<td>${10 le 10}</td>
			</tr>
			<tr>
				<td>${'${'}10ge 10}</td>
				<td>${10 ge 10}</td>
			</tr>
			<tr>
				<td>${'${'}10eq 10}</td>
				<td>${10 eq 10}</td>
			</tr>
		</table>
	</div>
	
	${header }
	<hr>
	${cookie.JSESSIONID.value }
	<hr>
	${param.some }
	<hr><hr>
	<h4>JSTL Core contains 14 tags </h4>
	
	The Core Tag Library
	<ul>
		<li>&lt;c:catch> Catches exceptions thrown in the action’s body</li>
		<li>&lt;c:choose> Chooses one of many code fragments</li>
		<li>&lt;c:forEach> Iterates over a collection of objects or
			iterates a fixed number of times</li>
		<li>&lt;c:forTokens> Iterates over tokens in a string</li>
		<li>&lt;c:if> Conditionally performs some functionality</li>
		<li>&lt;c:import> Imports a URL</li>
		<li>&lt;c:otherwise> Specifies default functionality in a</li>
		<li>&lt;c:choose> action</li>
		<li>&lt;c:out> Sends output to the current JspWriter</li>
		<li>&lt;c:param> Specifies a URL parameter for</li>
		<li>&lt;c:import> or</li>
		<li>&lt;c:url></li>
		<li>&lt;c:redirect> Redirects a response to a specified URL</li>
		<li>&lt;c:remove> Removes a scoped variable</li>
		<li>&lt;c:set> Creates a scoped variable</li>
		<li>&lt;c:url> Creates a URL, with URL rewriting as appropriate</li>
		<li>&lt;c:when> Specifies one of several conditions in a</li>
		<li>&lt;c:choose> action
	</ul>
	
	<hr>
	    The JSTL core library can be divided into four distinct functional areas: 
	<li>General-purpose actions     Used to manipulate the scoped variables (out , set , remove, catch) </li>
	<li>Conditional actions         Used for conditional processing within a JSP page (if, choose, when, otherwise) </li>
	<li>Iterator actions         Used to iterate through collections of objects (forEach, forTokens) </li>
	<li>URL-related actions         Used for dealing with URL resources in a JSP page (import, redirect, url) </li>
	<hr>
	<h3>General-purpose actions (out , set , remove, catch)</h3>
	

	<div style="border: 1px solid black">
		<c:set target="${books}" property="1" value="Java web dev"  /> <!-- will produce code like books.put("1","Book title 1") --> 
		
		<br>Books size: ${books.size()}<br>
		${books["1"]}
	</div>
	
	<c:set value="Value in session scope" var="fromSetTag" scope="session"/>
	<c:out value="${sessionScope.fromSetTag}"/>
	<c:out value="${jspfilter}" default="no jspfilter varibale"></c:out><!-- Value from filter  -->
	<hr>
	<!-- Testing c:remove tag -->
	<c:remove var="toDelete" scope="request"/>
	${toDelete} ${sessionScope.toDelete} 
	
	<hr>
	<!-- Testing c:catch tag -->
	<c:catch var="exc">
	<%
		// throwing exception
		Map<String,String> books = (Map<String,String>)session.getAttribute("books"); 
		books.get("2").toString(); // Exception , only 1 element is present in books map
	%>
	</c:catch>
	<div style="border: 2px solid red">
		${exc} - happened
	</div>
	<hr>
	<hr>
	<h3>JSTL Conditional tags</h3>

	<c:set var="number" value="9" />
	<c:if test="${ number < 10}">
		<c:out value="number is less than 10" />
	</c:if>
	<h5>
	<c:if test="${number < 100 }" var="exportedFromIf" scope="session"/>
	</h5>
	${exportedFromIf}

	<br>
	<c:set var="ten" value="10" scope="request"/>
	
	<c:choose>
		<c:when test="${ten < 3 and ten >20 }">
			ten < 3 and ten >20
		</c:when>
		<c:when test="${ten > 5  and ten < 14}">
			ten > 5  and ten < 14
		</c:when>
		<c:otherwise>
			Otherwise block
		</c:otherwise>
	</c:choose>
	
	<hr><hr>
	<h3>Url based tags</h3>
	<!-- If there is no connection to internet will be exception -->
	<c:catch var="netException">
		<div style="border: 3px solid blue"> 
			<%-- <c:import url="http://www.boostmobile.com/_vendors/_includes/footer/">
				<c:param name="search" value="sms"></c:param>
			</c:import> --%> <!--  Long with bad internet connection -->
		</div>
		
	</c:catch>
	
	<c:url value="/index.jsp"/> <!-- get localhost:8080/chap2_BookstoreJSPAndJSTL/index.jsp -->
	
	<hr>
	<%-- <c:redirect url="http://google.com"/> --%> <!-- redirecting to google.com -->
	
	<hr><hr>
	<h3>Iterating through collections using JSTL</h3>
	<ul>
		<c:forEach var="item" items="${array}">
			<li>${item}</li>
		</c:forEach>
	</ul>

	<c:forEach var="i" begin="1" end="3">
		Item <c:out value="${i}"/><p>
	</c:forEach>
	
	<div style="border: 1px solid dotted;">
		<c:forEach items="${map}" var="item">
			<p> ${item.key} - ${item.value}</p>
		</c:forEach>
	</div>
	
	<hr>
	<h3>c:forEach - interesing tag in JSTL</h3>
	<c:forTokens items="Clojure,Groovy,Java, Scala" delims="," var="lang">
		<c:out value="${lang}"/><p>
	</c:forTokens>
</body>
</html>
