<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8" isELIgnored="false" %>
<%@page import="java.util.Date"%>
    <%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt"%>
<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
<title>Insert title here</title>
</head>
<body>

    <!-- Manually set user locale -->
    <%-- <fmt:setLocale value="en"/>  --%>

    <fmt:message key="greeting" var="g" />
    ${g}

    ${g}

    ${param}
    <hr>

     <!-- Getting context param from web.xml -->
    Bundle using for L10n (bundle is filename in filename_en.properties, filename_uk.properties): <br>
    <b>${pageContext.servletContext.getInitParameter("javax.servlet.jsp.jstl.fmt.localizationContext")}</b>

    <hr>
    <div>Working with formatting dates with JSTL fortatting tags</div>
    <p>Date using EL :  ${date }</p>
    <p>Date using JSTL formatting 'short' : <fmt:formatDate value="${date}" dateStyle="short"/></p>
    <p>Date using JSTL formatting 'long' : <fmt:formatDate value="${date}" dateStyle="long"/></p>

    <hr>

    <div>Working with formatting numbers with JSTL </div>
    <fmt:formatNumber type="currency" value="${2500000}" currencyCode="UAH" />
</body>
</html>