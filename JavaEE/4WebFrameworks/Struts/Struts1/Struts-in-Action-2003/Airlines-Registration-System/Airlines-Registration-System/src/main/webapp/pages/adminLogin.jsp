<%@ include file="header.jsp"%>
<title>Admin Login</title>
<div class="masthead  ">
    <div class="container h-100  d-flex justify-content-center align-items-center">
        <div class="formcontainer w-100">
            <form action="welcomeAdmin.do" class="" method="post">
                <div class="mb-3 row">
                    <label class="form-label text-white col-3" for="inputEmail">Email</label>
                    <div class="col-9">
                        <input type="email" class="form-control" name="email"
                               id="inputEmail" placeholder="Email" >
                        <html:errors property="name"/>
                    </div>
                </div>
                <div class="mb-3 row">
                    <label class="form-label text-white col-3" for="inputPassword">Password</label>
                    <div class="col-9">
                        <input type="password" class="form-control " name="password"
                               id="inputPassword" placeholder="Password" >
                        <html:errors property="pass"/>
                        <% String value =(String)request.getAttribute("wrongAuth"); %>
                        <% if(value != null){ %>
                        <p style="color:red;">Wrong Credentials!</p>
                        <% } %>
                    </div>
                </div>

                <div class="">
                    <button type="submit" value="adminLogin" name="action"
                            class="btn btn-outline-success float-right">Sign in</button>
                </div>
            </form>
            <form action="user.do" method="post" id="userForm">
                <label class="form-check-label text-white" for="submit">Forgot Password ? </label>
                <button type="submit" class="btn btn-outline-info" name="action" value="forgotPass">Click Here</button>
            </form>
        </div>
    </div>
</div>


<%@ include file="footer.jsp"%>