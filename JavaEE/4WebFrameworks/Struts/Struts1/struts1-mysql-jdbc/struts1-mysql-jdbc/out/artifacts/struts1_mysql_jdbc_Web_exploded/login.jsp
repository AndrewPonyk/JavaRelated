
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@taglib uri="http://struts.apache.org/tags-bean" prefix="bean"%>
<%@taglib uri="http://struts.apache.org/tags-html" prefix="html"%>
<%@taglib uri="http://struts.apache.org/tags-logic" prefix="logic"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>

<html>
<head>
    <title>Login  ::</title>
</head>
<body>
Login page <br/>
<c:set var="model" value="${model}"/>
<b>
    ${model.message}</b>
<html:form action="/validateLogin">
    <html:text property="user"/>
    <html:password property="password"/>
    <input type="submit" value="submit"/>
</html:form>
</body>
</html>
