<%@ include file="header.jsp"%>
<title>Registration</title>
<div class="masthead">
    <div
            class="container h-100  d-flex justify-content-center align-items-center">
        <div class="formcontainer w-100">
            <form class="form-horizontal" action="userLogin.do" method="post">
                <div class="form-group row">
                    <label class="control-label text-white col-sm-3" for="email">Email:</label>
                    <div class="col-sm-9">
                        <input type="email" class="form-control" id="email"
                               placeholder="Enter email" name="email">
                        <html:errors property="name" />
                    </div>
                </div>
                <div class="form-group row">
                    <label class="control-label text-white col-sm-3" for="pwd">Password:</label>
                    <div class="col-sm-9">
                        <input type="password" class="form-control" id="pwd"
                               placeholder="Enter password" name="password">
                        <html:errors property="pass" />

                    </div>
                </div>
                <div class="form-group row">
                    <label class="control-label text-white col-sm-3" for="pwd">Confirm Password:</label>
                    <div class="col-sm-9">
                        <input type="password" class="form-control" id="pwd"
                               placeholder="Confirm password" name="conpassword">
                        <html:errors property="conpass" />
                        <html:errors property="notmatched" />
                    </div>
                </div>
                <div class="form-group">
                    <div class="">
                        <button type="submit" class="btn btn-outline-success float-right" name="action" value="register">Sign Up</button>
                    </div>
                </div>
            </form>
            <div> Already Have Acount ? <a class="btn btn-outline-info" href="login.do">Login Here</a></div>
        </div>
    </div>
</div>


<%@ include file="footer.jsp"%>