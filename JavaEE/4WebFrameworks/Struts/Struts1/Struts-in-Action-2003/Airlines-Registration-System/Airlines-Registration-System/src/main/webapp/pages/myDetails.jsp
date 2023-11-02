<%@page import="com.crossairlines.pojo.User"%>
<%@ include file="adminHeader.jsp" %>
<div class="masthead">

    <div id="book" class="text-white h-100 d-flex justify-content-center align-items-center">
        <div class="container p-5 " id="userDetails">
            <%
                User user = (User) request.getSession().getAttribute("userDetail");
                if (user == null) {
                    user = new User();
                }
            %>
            <form action="welcomeAdmin.do" method="post">
                <div class="form-group row">
                    <label for="authid" class="col-3">User Auth Id : </label> <input
                        type="number" class="form-control col-9" id="authid" name="userAuthId" disabled
                        value="<%=user.getUserAuthId()%>" placeholder="Enter email">
                </div>
                <div class="form-group row">
                    <label for="email" class="col-3">Email : </label> <input
                        type="email" class="form-control col-9" id="email" disabled
                        value="<%=user.getEmail()%>" name="email" placeholder="Enter email">
                </div>
                <div class="form-group row">
                    <label for="password" class="col-3">Password : </label> <input
                        type="text" class="form-control col-9" disabled
                        value="<%=user.getPassword()%>" name="password" id="password"
                        placeholder="Password">
                </div>
                <div class="form-group row">
                    <label for="firstName" class="col-3">First Name : </label> <input
                        type="text" class="form-control col-9 disabled" disabled
                        id="firstName" name="firstName" value="<%=user.getFirstName()%>"
                        disabled placeholder="First Name">
                </div>
                <div class="form-group row">
                    <label for="middleName" class="col-3">Middle Name : </label> <input
                        type="text" class="form-control col-9 disabled" disabled
                        id="middleName" name="middleName"
                        value="<%=user.getMiddleName()%>" disabled
                        placeholder="Middle Name">
                </div>
                <div class="form-group row">
                    <label for="lastName" class="col-3">Last Name : </label> <input
                        type="text" class="form-control col-9 disabled" id="lastName"
                        name="lastName" value="<%=user.getLastName()%>" disabled
                        placeholder="lastName ">
                </div>
                <div class="form-group row">
                    <label for="dob" class="col-3">Date Of Birth : </label> <input
                        type="date" class="form-control col-9 disabled" id="dob"
                        name="dobt" value="" disabled pattern="\d{4}-\d{2}-\d{2}"
                        placeholder="lastName ">
                </div>

                <div class="form-group row">
                    <label for="mobileNo" class="col-3">Mobile NO. : </label> <input
                        type="number" class="form-control col-9 disabled" disabled
                        value="<%=user.getMobileNo()%>" id="mobileNo" name="mobileNo"
                        placeholder="Mobile No.">
                </div>
                <div class="form-group row">
                    <label for="address" class="col-3">Address : </label> <input
                        type="text" class="form-control col-9 disabled" disabled
                        value="<%=user.getAddress()%>" id="address" name="address"
                        placeholder="Address">
                </div>
                <button type="submit" name="action" value="updateUserDetails"
                        class="btn float-right btn-outline-primary">Edit</button>
            </form>
        </div>
    </div>
</div>
<script>
    const showUserDetails = document.getElementById("showUserDetails");
    const userDetails = document.getElementById("userDetails");
    showUserDetails.addEventListener("click",()=>{
        userDetails.classList.toggle("hide");
        if(!userDetails.classList.contains("hide")){
            showUserDetails.innerHTML="Hide My Details";
        }
        else{
            showUserDetails.innerHTML="Show My Details";
        }
    });
</script>
<%@ include file="footer.jsp" %>