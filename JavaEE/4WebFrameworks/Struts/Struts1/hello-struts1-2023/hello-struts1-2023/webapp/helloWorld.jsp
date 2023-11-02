<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@taglib uri="http://struts.apache.org/tags-bean" prefix="bean"%>
<html>
<head>
    <title>Title</title>
</head>
<body>
This is success mapping from struts action
<h1><bean:write name="helloWorldForm" property="greeting" /></h1>


</body>
</html>
