<%@page language="java" contentType="text/html"%>
<%@page import="java.util.Enumeration"%>
<%@page import="java.util.Hashtable"%>
<%@page import="java.util.List"%>
<%@page import="java.util.ArrayList"%>
<%@page import="java.util.Iterator"%>
<%@page import="com.apress.bookweb.model.Book"%>
<%@page import="com.apress.bookweb.model.Category"%>
<%
	String param1 = application.getInitParameter("param1");
%>


</head>
<div class="leftbar">
	<ul id="menu">
		<li><div>
				<a class="link1" href="/<%=param1%>"> <span class="label"
					style="margin-left: 15px;">Home</span>
				</a>
			</div></li>
		<li><div>
				<a class="link1" href="/<%=param1%>?action=allBooks"><span
					style="margin-left: 15px;" class="label">All Books</span></a>
			</div></li>
		<li><div>
				<span class="label" style="margin-left: 15px;">Categories</span>
			</div>
			<ul>
				<%
					List<Category> categoryList1 = (List<Category>) application
							.getAttribute("categoryList");
					Iterator<Category> iterator1 = categoryList1.iterator();
					while (iterator1.hasNext()) {
						Category category1 = (Category) iterator1.next();
				%>
				<li><a class="label"
					href="/<%=param1%>?action=category&categoryId=<%=category1.getId()%>&category=<%=category1.getCategoryDescription()%>"><span
						class="label" style="margin-left: 30px;"><%=category1.getCategoryDescription()%></span></a>
				</li>
				<%
					}
				%>
			</ul></li>
		<li><div>
				<span class="label" style="margin-left: 15px;">Contact Us</span>

			</div></li>
	</ul>
	<form class="search">
		<input type="hidden" name="action" value="search" /> <input id="text"
			type="text" name="keyWord" size="12" /> <span
			class="tooltip_message">?</span>
		<p />
		<input id="submit" type="submit" value="Search" />
	</form>


</div>





