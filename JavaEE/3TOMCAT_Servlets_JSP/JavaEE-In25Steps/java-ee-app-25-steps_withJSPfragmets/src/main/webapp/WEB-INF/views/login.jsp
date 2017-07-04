<%@include file="../common/header.jspf"%>

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

<%@include file="../common/footer.jspf"%>