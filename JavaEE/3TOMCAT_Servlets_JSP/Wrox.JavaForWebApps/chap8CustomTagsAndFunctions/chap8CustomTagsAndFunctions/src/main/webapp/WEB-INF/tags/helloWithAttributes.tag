<%@taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<%@ tag language="java" pageEncoding="UTF-8" isELIgnored="false"%>
<%@ attribute name="htmlTitle" type="java.lang.String"
	rtexprvalue="true" required="true"%>
<%@ attribute name="csvData" type="java.lang.String" rtexprvalue="true"
	required="true"%>

<div style="border: 1px solid red">
	<H2>This is hello world tag</H2>

	<div style="color: lime;">${htmlTitle }</div>
	<p>Coma separated value passed in parameter formtatted by tag:</p>
	<ul>
		<c:forTokens items="${csvData }" delims="," var="item">
			<li>${item }</li>
		</c:forTokens>
	</ul>

Body of the tag : <i><jsp:doBody /></i>
</div>
