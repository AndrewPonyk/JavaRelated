<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8"%>
<%@taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@taglib uri="http://java.sun.com/jsp/jstl/xml" prefix="x"%>
<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
<title>Using XML Tags</title>
</head>
<body>


    <c:import url="http://localhost:8080/chap7JSTL/rss.xml" var="rssDoc"></c:import>

    <x:parse doc="${rssDoc}" var="parsedDoc"/>

    <div>
        Document title:
        <x:out select="$parsedDoc/rss/channel/title"/>
    </div>

	<div>
		Items in document:
		<ul>
			<x:forEach select="$parsedDoc/rss/channel//item/title/text()" var="i">
			<li>
                <%-- <x:out select="@title" /> --%> <!-- @ is for attributes -->
                <%-- ${i.getClass()} --%>
                ${i.wholeText }
			</li>
			</x:forEach>
		</ul>
	</div>
</body>
</html>