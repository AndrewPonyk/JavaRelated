<%@include file="../common/header.jspf"%>

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
    Your New Action Item:
    <form method="POST" action="add-todo.do">
        <fieldset class="form-group">
            <label>Description:</label>
            <input name="todo" type="text" class="form-control"/>
        </fieldset>
        <fieldset class="form-group">
            <label>Category:</label>
            <input name="category" type="text" class="form-control"/>
        </fieldset>
        <input name="add" type="submit" value="Add" class="btn btn-success"/>
    </form>
</div>

<%@include file="../common/footer.jspf"%>