<%@ page import="java.util.Vector, com.wrox.PageVisit, java.util.Date"%>
<%@ page import="java.text.SimpleDateFormat"%>
<%!private static String toString(long timeInterval) {
		if (timeInterval < 1000)
			return "less than one second";
		if (timeInterval < 60000)
			return (timeInterval / 1000) + " seconds";
		return "about " + (timeInterval / 60000) + " minutes";
	}%>
<%
	SimpleDateFormat f = new SimpleDateFormat(
			"EEE, d MMM yyyy HH:mm:ss Z");
%>
<!DOCTYPE html>
<html>
<head>
<title>Session Activity Tracker</title>
</head>
<body>
	<h2>Session Properties</h2>
	Session ID:
	<%=session.getId()%><br /> Session is new:
	<%=session.isNew()%><br /> Session created:
	<%=f.format(new Date(session.getCreationTime()))%><br />
	<h2>Page Activity This Session</h2>
	<%
		@SuppressWarnings("unchecked")
		Vector<PageVisit> visits = (Vector<PageVisit>) session
				.getAttribute("activity");
		for (PageVisit visit : visits) {
			out.print(visit.request);
			if (visit.ipAddress != null)
				out.print(" from IP " + visit.ipAddress.getHostAddress());
			out.print(" (" + f.format(new Date(visit.enteredTimestamp)));
			if (visit.leftTimestamp != null) {
				out.print(", stayed for "
						+ toString(visit.leftTimestamp
								- visit.enteredTimestamp));
			}
			out.println(")<br />");
		}
	%>
</body>
</html>