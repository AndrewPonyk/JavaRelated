<%@ page isELIgnored="false" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<!DOCTYPE html>
<html>
<head>
    <title>Todos</title>
    <link href="webjars/bootstrap/3.3.6/css/bootstrap.min.css"
          rel="stylesheet">

    <style>
        .footer {
            position: absolute;
            bottom: 0;
            width: 100%;
            height: 60px;
            background-color: #f5f5f5;
        }
    </style>
</head>

<body>

<nav class="navbar navbar-default">

    <a href="/" class="navbar-brand">Brand</a>

    <ul class="nav navbar-nav">
        <li class="active"><a href="#">Home</a></li>
        <li><a href="list-todos.do">Todos</a></li>
        <li><a href="http://www.in28minutes.com">In28Minutes</a></li>
    </ul>

    <ul class="nav navbar-nav navbar-right">
        <li><a href="logout.do">Logout</a></li>
    </ul>

</nav>

<div class="container">
    <H1>Welcome ${name}</H1>

    <table class="table table-striped table-bordered">
        <caption>Your Todos are</caption>
        <thead>
        <th>Description</th>
        <th>Category</th>
        <th>Actions</th>
        </thead>
        <tbody>
        <c:forEach items="${todos}" var="todo">
            <tr>
                <td>${todo.name}</td>
                <td>${todo.category}</td>
                <td>&nbsp;&nbsp;<a class="btn btn-danger"
                                   href="delete-todo.do?todo=${todo.name}&category=${todo.category}">Delete</a></td>
            </tr>
        </c:forEach>
        </tbody>
    </table>

    <p>
        <font color="red">${errorMessage}</font>
    </p>
    <a
            href="add-todo.do">Add New Todo</a>
</div>

<footer class="footer">
    <div>footer content</div>
</footer>

<script src="webjars/jquery/1.9.1/jquery.min.js"></script>
<script src="webjars/bootstrap/3.3.6/js/bootstrap.min.js"></script>

</body>

</html>