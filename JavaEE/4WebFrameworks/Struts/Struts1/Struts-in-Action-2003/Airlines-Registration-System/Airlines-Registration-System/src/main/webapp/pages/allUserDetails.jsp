<%@page import="com.crossairlines.pojo.User"%>
<%@page import="java.util.List"%>
<%@ page language="java" contentType="text/html; charset=UTF-8"
         pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Insert title here</title>
</head>
<body>
<div
        class=" container h-100 d-flex justify-content-center align-items-center">
    <table class="table table-hover">
        <thead class="thead-dark">
        <tr>
            <th scope="col">Sr No.</th>
            <th scope="col">Email</th>
            <th scope="col">Password</th>
            <th scope="col">IsAdmin</th>
        </tr>
        </thead>
        <tbody class="text-white">
        <%
            List<User> userAuthList = (List<User>) request.getAttribute("userAuthDetails");
            for (User userAuth : userAuthList) {
        %>
        <tr>
            <td><%=userAuth.getUserAuthId() %></td>
            <td><%=userAuth.getEmail() %></td>
            <td><%=userAuth.getPassword()%></td>
            <td><%=userAuth.getIsAdmin()%></td>
        </tr>
        <%
            }
        %>
        </tbody>
    </table>
</div>
</body>
</html>