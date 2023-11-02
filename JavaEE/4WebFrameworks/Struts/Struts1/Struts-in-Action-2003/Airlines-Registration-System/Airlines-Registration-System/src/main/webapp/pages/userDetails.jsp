<%@page import="com.crossairlines.pojo.User"%>
<%@ include file="adminHeader.jsp"%>
<title>Update User Details</title>
<div class="masthead">
    <div
            class="container h-100 d-flex justify-content-center align-items-center ">
        <div id="" class="text-white w-100">
            <div class="container p-5" id="userDetails">
                <%
                    User user = (User) request.getAttribute("user");
                    if (user == null) {
                        user = new User();
                    }
                %>
                <form action="welcomeAdmin.do" method="post" id="myForm" onsubmit="setDate()">
                    <div class="form-group row">
                        <label for="authid" class="col-3">User Auth Id : </label> <input
                            type="number" class="form-control col-9" id="authid" disabled
                            value="<%=user.getUserAuthId()%>" placeholder="Enter email">
                    </div>
                    <div class="form-group row">
                        <label for="email" class="col-3">Email : </label> <input
                            type="email" class="form-control col-9" id="email" name="email"
                            value="<%=user.getEmail()%>" placeholder="Enter email">
                    </div>
                    <div class="form-group row">
                        <label for="password" class="col-3">Password : </label> <input
                            type="text" class="form-control col-9"
                            value="<%=user.getPassword()%>" id="password" name="password"
                            placeholder="Password">
                    </div>
                    <div class="form-group row">
                        <label for="isAdmin" class="col-3">Is Admin : </label>
                        <input type="hidden" name="isAdmin" id="isAdminHidden">
                        <input
                                type="checkbox" class="col-3" id="isAdmin">
                        <span id="msg" class="col-6"></span>
                    </div>
                    <div class="form-group row">
                        <label for="firstName" class="col-3">First Name : </label> <input
                            type="text" class="form-control col-9 "
                            id="firstName" name="firstName" value="<%=user.getFirstName()%>"
                            placeholder="First Name">
                    </div>
                    <div class="form-group row">
                        <label for="middleName" class="col-3">Middle Name : </label> <input
                            type="text" class="form-control col-9 "
                            id="middleName" name="middleName"
                            value="<%=user.getMiddleName()%>"
                            placeholder="Middle Name">
                    </div>
                    <div class="form-group row">
                        <label for="lastName" class="col-3">Last Name : </label> <input
                            type="text" class="form-control col-9 " id="lastName"
                            name="lastName" value="<%=user.getLastName()%>"
                            placeholder="lastName ">
                    </div>
                    <div class="form-group row">
                        <label for="dob" class="col-3">Date Of Birth : </label> <input
                            type="date" class="form-control col-9 " id="dob"
                            name="dobt" value=<%=user.getDob() %>
                                    placeholder="Date Of Bith" pattern="\d{4}-\d{2}-\d{2}">
                    </div>

                    <div class="form-group row">
                        <label for="mobileNo" class="col-3">Mobile NO. : </label> <input
                            type="number" class="form-control col-9 "
                            value="<%=user.getMobileNo()%>" id="mobileNo" name="mobileNo"
                            placeholder="Mobile No.">
                    </div>
                    <div class="form-group row">
                        <label for="address" class="col-3">Address : </label> <input
                            type="text" class="form-control col-9 "
                            value="<%=user.getAddress()%>" id="address" name="address"
                            placeholder="Address">
                    </div>
                    <button type="submit" name="action" value="updateUserDetails"
                            class="btn float-right btn-outline-primary">Update</button>
                </form>
            </div>
        </div>
    </div>
</div>

<script type="text/javascript">


    var checkbox = document.getElementById("isAdmin");
    var msg = document.getElementById("msg");
    var isadmin = document.getElementById("isAdminHidden");
    checkbox.addEventListener("click",()=>{

        if(checkbox.checked==true){
            msg.innerHTML="Update User as Admin";
            isadmin.value=true;
        }
        else{
            msg.innerHTML="Update User as Customer";
            isadmin.value=false;
        }
    });
    if(<%=user.getIsAdmin() %>){
        msg.innerHTML="Admin";
        checkbox.checked=true;
        isadmin.value=true;
    }
    else{
        msg.innerHTML="Customer";
        checkbox.checked=false;
        isadmin.value=false;
    }
</script>
<%@ include file="footer.jsp" %>