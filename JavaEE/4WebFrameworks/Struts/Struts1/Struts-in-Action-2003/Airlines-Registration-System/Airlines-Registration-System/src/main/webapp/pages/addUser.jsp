<%@page import="org.apache.struts.action.ActionForm"%>
<%@page import="com.crossairlines.pojo.User"%>
<%@ include file="adminHeader.jsp"%>
<% ActionForm form = null; %>
<div class="masthead">
    <div
            class="container h-100 d-flex justify-content-center align-items-center ">
        <div id="" class="text-white w-100">
            <div class="container p-5 " id="userDetails">
                <form action="welcomeAdmin.do" method="post">
                    <div class="form-group row ">
                        <label for="email" class="col-3">Email : </label> <input
                            type="email" class="form-control col-9" name="email" id="email"
                            value="" placeholder="Enter email">
                    </div>
                    <div class="form-group row">
                        <label for="password" class="col-3">Password : </label> <input
                            type="text" class="form-control col-9"
                            value="" id="password" name="password"
                            placeholder="Password">
                    </div>
                    <div class="form-group row">
                        <label for="isAdmin" class="col-3">Is Admin : </label> <input
                            type="checkbox" class="col-3"
                            value="" id="isAdmin" name="isAdmin">
                        <span id="msg" class="col-6" >Add User As Customer </span>
                    </div>
                    <div class="form-group row">
                        <label for="firstName" class="col-3">First Name : </label> <input
                            type="text" class="form-control col-9 "
                            id="firstName" name="firstName" value=""
                            placeholder="First Name">
                    </div>
                    <div class="form-group row">
                        <label for="middleName" class="col-3">Middle Name : </label> <input
                            type="text" class="form-control col-9 "
                            id="middleName" name="middleName"
                            value=""
                            placeholder="Middle Name">
                    </div>
                    <div class="form-group row">
                        <label for="lastName" class="col-3">Last Name : </label> <input
                            type="text" class="form-control col-9 " id="lastName"
                            name="lastName" value=""
                            placeholder="Last Name ">
                    </div>
                    <div class="form-group row">
                        <label for="dob" class="col-3">Date Of Birth: </label> <input
                            type="date" class="form-control col-9 " id="dob"
                            name="dobt"  pattern="\d{4}-\d{2}-\d{2}"
                            placeholder="Date Of Birth">
                    </div>

                    <div class="form-group row">
                        <label for="mobileNo" class="col-3">Mobile NO. : </label> <input
                            type="number" class="form-control col-9 "
                            value="" id="mobileNo" name="mobileNo"
                            placeholder="Mobile No.">
                    </div>
                    <div class="form-group row">
                        <label for="address" class="col-3">Address : </label> <input
                            type="text" class="form-control col-9 "
                            value="" id="address" name="address"
                            placeholder="Address">
                    </div>
                    <button type="submit" name="action" value="addNewUser"
                            class="btn float-right btn-outline-primary">Add New User</button>
                </form>
            </div>
        </div>
    </div>
</div>
<script type="text/javascript">
    var checkbox = document.getElementById("isAdmin");
    var msg = document.getElementById("msg");
    checkbox.addEventListener("click",()=>{

        if(checkbox.checked==true){
            msg.innerHTML="ADD User as Admin";
            checkbox.value=true;
        }
        else{
            msg.innerHTML="ADD User as Customer";
            checkbox.value=false;
        }
    })

</script>
<%@ include file="footer.jsp"%>