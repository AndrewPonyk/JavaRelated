<%@ include file="header.jsp"%>
<title>User Welcome <%=request.getAttribute("userid")%></title>
<% Cookie[] cookies = request.getCookies();
    if(cookies[0] == null){
        request.getRequestDispatcher("login.do").forward(request, response);
    }
%>
<div class="masthead">


</div>
<div class="section" id="booking">
    <div class="container">
        <table class="table p-5 table-hover bg-dark">
            <thead class="thead-dark">
            <tr>
                <th>Flight Name</th>
                <th>Origin Airport</th>
                <th>destination Airport</th>
                <th>Departure Time</th>
                <th>Arrival Time</th>
                <th>Distance</th>
                <th>Fare</th>
                <th>Booked Date</th>
            </tr>
            </thead>
            <tbody class="text-white">
            <tr>
                <td>Flight Name</td>
                <td>Origin Airport</td>
                <td>destination Airport</td>
                <td>Departure Time</td>
                <td>Arrival Time</td>
                <td>Distance</td>
                <td>Fare</td>
                <td>Operation</td>
            </tr>

            </tbody>
        </table>
    </div>
</div>


<%@ include file="footer.jsp"%>