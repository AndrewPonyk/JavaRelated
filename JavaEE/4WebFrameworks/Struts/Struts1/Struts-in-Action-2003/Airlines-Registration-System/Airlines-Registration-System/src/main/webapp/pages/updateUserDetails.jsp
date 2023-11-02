<%@page import="com.crossairlines.pojo.User"%>
<%@page import="java.util.List"%>
<%@ include file="adminHeader.jsp"%>
<title>All Users Details</title>
<div class="masthead">
    <div
            class="container-fluide h-100  d-flex justify-content-center align-items-center">
        <div class="section-center w-100 px-4">
            <table class="table table-hover">
                <thead class="thead-dark">
                <tr>
                    <th>User Id</th>
                    <th>Email</th>
                    <th>Password</th>
                    <th>Is Admin</th>
                    <th>First Name</th>
                    <th>Middle Name</th>
                    <th>Last Name</th>
                    <th>Date Of Birth</th>
                    <th>Address</th>
                    <th colspan="2" class="text-center">Operation</th>
                </tr>
                </thead>
                <tbody class="text-white">
                <%
                    List<User> users = (List<User>) request.getSession().getAttribute("usersDetail");
                    if (users == null)
                        request.getRequestDispatcher("/adminHome.do").forward(request, response);
                    else
                        for (User user : users) {
                %>
                <tr style="
                    <% if(user.getIsAdmin()){ %> background:rgba(160,224,77,0.3);
                    <% }else{ %> background:rgba(81,121,170,0.3);<%}%>
                        ">
                    <td><%=user.getUserAuthId()%></td>
                    <td><%=user.getEmail()%></td>
                    <td><%=user.getPassword()%></td>
                    <td><%=user.getIsAdmin()%></td>
                    <td><%=user.getFirstName()%></td>
                    <td><%=user.getMiddleName()%></td>
                    <td><%=user.getLastName()%></td>
                    <td><%=user.getDob()%> </td>
                    <td><%=user.getAddress()%></td>
                    <td>
                        <form action="welcomeAdmin.do"  method="post" class="form-inline">
                            <input type="hidden" name="userAuthId" value=<%=user.getUserAuthId()%>>
                            <input type="submit" name="action" class="btn btn-outline-primary"
                                   value="update">

                        </form>
                    </td>
                    <td>
                        <form action="welcomeAdmin.do" method="post" class="form-inline">
                            <input type="hidden" name="userAuthId" value=<%=user.getUserAuthId()%>>
                            <input type="submit"
                                   name="action" class="btn btn-outline-danger"
                                   value="delete">
                        </form>
                    </td>
                </tr>

                <%
                        }
                %>

                </tbody>
            </table>
        </div>
    </div>
</div>
<%@ include file="footer.jsp"%>