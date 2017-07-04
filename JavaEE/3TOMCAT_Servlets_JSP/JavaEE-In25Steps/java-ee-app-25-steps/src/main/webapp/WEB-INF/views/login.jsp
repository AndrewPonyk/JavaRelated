<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<%@ page isELIgnored="false" %>
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
        <li><a href="login.do">Login</a></li>
    </ul>

</nav>

<div class="container">
    <form action="login.do" method="post">
        <p>
            <font color="red">${errorMessage}</font>
        </p>
        <fieldset class="form-group">
         <label>Name:</label>
            <input type="text" name="name" class="form-control"/>
        </fieldset>
        <fieldset class="form-group">
            <label>Password:</label>
            <input type="password" name="password" class="form-control"/>
        </fieldset>
        <input type="submit" value="Login" class="btn btn-success form-control"/>
    </form>
</div>

<footer class="footer">
    <div>footer content</div>
</footer>

<script src="webjars/jquery/1.9.1/jquery.min.js"></script>
<script src="webjars/bootstrap/3.3.6/js/bootstrap.min.js"></script>

</body>

</html>