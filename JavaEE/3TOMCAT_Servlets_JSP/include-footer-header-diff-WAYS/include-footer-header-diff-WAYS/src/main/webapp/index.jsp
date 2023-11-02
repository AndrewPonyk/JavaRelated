<%@ page import="java.time.LocalDateTime" %>
<html>
<body>
<h2>Hello World!</h2>
This is java time now: <%= LocalDateTime.now().toString() %>
1111
<div>This is some data from request obj: <%= request.getHeader("Accept-Language") %></div>

<%@include file="WEB-INF/pages/footer.jsp"%>
<jsp:include page="WEB-INF/pages/footer.jsp"/>


<br>
<div>here we have CONDITIONAL include (which is impossible using %@ include </div>
<% if (request.getParameter("includeFooter") != null) { %>
<%@include file="WEB-INF/pages/footer.jsp"%>
<% } %>
</body>
</html>
