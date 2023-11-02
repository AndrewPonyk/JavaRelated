<%@page import="com.crossairlines.pojo.FlightDetails"%>
<%@page import="java.util.List"%>
<%@ include file="adminHeader.jsp"%>

<title>Welcome Admin</title>
<div class="masthead">
    <p class="text-center text-white" style="font-size: 25px;">All
        Flight Details</p>
    <% List<FlightDetails> flightList = (List<FlightDetails>)request.getSession().getAttribute("allFlightDetails");  %>
    <div class="p-2 h-100 d-flex justify-content-center align-items-center">
        <div class="text-white  w-100 ">

            <div class="section-center w-100 px-4">
                <table class="table table-hover">
                    <thead class="thead-dark">
                    <tr>
                        <th>Flight Id</th>
                        <th>From Country</th>
                        <th>Destination Country</th>
                        <th>origin city</th>
                        <th>Destination City</th>
                        <th>Origin Airport</th>
                        <th>Destination Airport</th>
                        <th>flightType</th>
                        <th>Company</th>
                        <th>departure Date</th>
                        <th>Departure Time</th>
                        <th>arrival Date</th>
                        <th>arrival Time</th>
                    </tr>
                    </thead>
                    <tbody class="text-white">
                    <% for(FlightDetails flightDetails : flightList){ %>
                    <tr>
                        <td><%=flightDetails.getFlightDetailsId() %></td>
                        <td><%=flightDetails.getFromCountry() %></td>
                        <td><%=flightDetails.getDestCountry() %></td>
                        <td><%=flightDetails.getOriginCity() %></td>
                        <td><%=flightDetails.getDestinationCity() %></td>
                        <td><%=flightDetails.getOriginAirport() %></td>
                        <td><%=flightDetails.getDestinationAirport() %></td>
                        <td><%=flightDetails.getFlightType() %></td>
                        <td><%=flightDetails.getCompany() %></td>
                        <td><%=flightDetails.getDepartDate() %></td>
                        <td><%=flightDetails.getDepartTime() %></td>
                        <td><%=flightDetails.getArrivalDate() %></td>
                        <td><%=flightDetails.getArrivalTime() %></td>
                    </tr>
                    <% } %>
                    </tbody>
                </table>
            </div>
        </div>
    </div>


</div>


<%@ include file="footer.jsp"%>