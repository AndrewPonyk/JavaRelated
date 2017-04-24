<%@ page language="java" contentType="text/html; charset=UTF-8"
	pageEncoding="UTF-8"%>
<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
<title>Insert title here</title>
</head>
<body>
	<fieldset>
		<legend>Hot deploy in Eclipse</legend>
		<div>1)Window->Preferences-->Workspace-> (Check 'Build
			automatically' and 'Refresh using native hooks and Pooling')</div>
		<div>2)In glassfish settings : Publishing::Automatically publish
			when resource change, and change 'Publishing interval' to 0</div>
	</fieldset>

</body>
</html>