<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="s" uri="/struts-tags" %>

<html>
<body>
<h2>Hello World</h2>
<hr/>
<s:form action="register">
  <s:textfield name="username"/>
  <s:textfield name="email"/>
  <s:submit/>
</s:form>
</body>
</html>
