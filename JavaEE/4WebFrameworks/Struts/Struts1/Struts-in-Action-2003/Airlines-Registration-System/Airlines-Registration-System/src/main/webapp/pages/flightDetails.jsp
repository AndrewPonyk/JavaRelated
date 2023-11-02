<%@page import="java.util.ArrayList"%>
<%@page import="java.util.HashMap"%>
<%@page import="com.crossairlines.daoimplementation.FlightDaoImp"%>
<%@page import="com.crossairlines.dao.FlightDao"%>
<%@page import="java.util.List"%>
<%@page import="com.crossairlines.pojo.FlightDetails"%>
<%@ include file="header.jsp"%>
<title>Flight Details</title>
<div class="masthead">
    <section class="" id="">
        <div class="">
            <div id="" class="section">
                <div class="section-center">
                    <div class="container">
                        <div class="row">
                            <div class="booking-form">
                                <form action="searchFlight.do" method="post">
                                    <div class="form-group">
                                        <div class="form-checkbox">
                                            <label for="one-way"> <input type="radio"
                                                                         id="one-way" value="one Way" name="flightType"> <span></span>One
                                                way
                                            </label> <label for="roundtrip"> <input type="radio" checked
                                                                                    id="roundtrip" value="round Trip" name="flightType">
                                            <span></span>Round trip
                                        </label> <label for="multi-city"> <input type="radio"
                                                                                 id="multi-city" name="flightType" value="multi City">
                                            <span></span>Multi-City
                                        </label>
                                        </div>
                                    </div>
                                    <div class="row">
                                        <div class="col-3">
                                            <div class="form-group">
                                                <span class="form-label">Flying from</span>
                                                <div>
                                                    <datalist id="originData">

                                                    </datalist>
                                                    <datalist id="destData">
                                                    </datalist>

                                                    <input class="form-control" id="origin" name="origin"
                                                           type="text" required placeholder="Origin City"
                                                           autoComplete="on" list="originData">
                                                </div>
                                            </div>
                                        </div>
                                        <div class="col-3">
                                            <div class="form-group">
                                                <span class="form-label">Flyning to</span>
                                                <div>

                                                    <input class="form-control" type="text" id="destination"
                                                           list="destData" autoComplete="on" name="destination"
                                                           required placeholder="City">

                                                </div>

                                            </div>
                                        </div>
                                        <div class="col-md-3">
                                            <div class="form-group">
                                                <span class="form-label">Departing</span> <input
                                                    class="form-control" name="departDate" type="date"
                                                    id="set_bdate" required>
                                            </div>
                                        </div>
                                        <div class="col-md-3">
                                            <div class="form-group">
                                                <span class="form-label">Returning</span> <input
                                                    class="form-control" id="returnDate" name="returnDate"
                                                    type="date">
                                            </div>
                                        </div>
                                        <div class="row"></div>

                                    </div>
                                    <div class="row">
                                        <div class="col-md-3">
                                            <div class="form-group">
                                                <span class="form-label">Mid Way Cities</span> <select
                                                    class="form-control" disabled id="multicity"
                                                    name="multicity">
                                                <option>Select In Between city</option>
                                                <option value="Mumbai">Mumbai</option>
                                                <option value="Delhi">Delhi</option>
                                                <option value="Kolkata">Kolkata</option>
                                            </select> <span class="select-arrow"></span>
                                            </div>
                                        </div>

                                        <div class="col-md-3">
                                            <div class="form-btn">
                                                <button type="submit" name="action" value="searchFlight"
                                                        class="submit-btn">Show flights</button>
                                            </div>
                                        </div>
                                    </div>
                                </form>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    </section>
</div>
<div class="section" id="booking">
    <div class="container-fluid">
        <table class="table p-5 table-hover bg-dark">
            <thead class="thead-dark">
            <tr>
                <th>Flight Name</th>
                <th>Origin City</th>
                <th>destination City</th>
                <th>Origin Airport</th>
                <th>destination Airport</th>
                <th>Departure Date</th>
                <th>Departure Time</th>
                <th>Arrival Time</th>
                <th>fare <select id="classType" form="myForm" name="classType">
                    <option value="Economy">Economy</option>
                    <option value="Business">Business</option>
                    <option value="firstClass">First Class</option>
                </select>
                </th>
                <th>Book Ticket</th>
            </tr>
            </thead>
            <tbody class="text-white">
            <%
                List<FlightDetails> flightDetails = (List<FlightDetails>) request.getAttribute("gotFlights");
                String message = "Sorry For inconvenience! No Flight Found in this Route! ";
                if (flightDetails.size() != 0) {
                    for (FlightDetails flightDetail : flightDetails) {
            %>
            <tr>
                <td><%=flightDetail.getCompany()%></td>
                <td><%=flightDetail.getOriginCity() %></td>
                <td><%=flightDetail.getDestinationCity()%></td>
                <td><%=flightDetail.getOriginAirport()%></td>
                <td><%=flightDetail.getDestinationAirport()%></td>
                <td><%=flightDetail.getDepartDate()%></td>
                <td><%=flightDetail.getDepartTime()%></td>
                <td><%=flightDetail.getArrivalTime()%></td>
                <td><span class="fare">5000</span></td>
                <td>
                    <form class="form-inline" action="bookTicket.do" id="myForm" method="post">
                        <input type="hidden" name="id"
                               value=<%=flightDetail.getFlightDetailsId()%> />
                        <button type="submit" class="btn btn-outline-info" name="action"
                                value="bookTicket">Book</button>
                    </form>
                </td>
            </tr>
            <script type="text/javascript">
                const flightType=document.getElementById("classType");
                var fare = document.getElementsByClassName("fare");
                for(var i=0; i<fare.length; i++){
                    fare[i].innerHtml = <%=flightDetail.getEconomyFare() %>;
                }
                flightType.addEventListener("change",()=>{
                    if(flightType.value=="Economy"){
                        for(var i=0; i<fare.length; i++){
                            fare[i].innerHTML = <%=flightDetail.getEconomyFare() %>;
                        }
                    }
                    if(flightType.value=="Business"){
                        for(var i=0; i<fare.length; i++){
                            fare[i].innerHTML = <%=flightDetail.getBusinessFare() %>;
                        }

                    }
                    if(flightType.value=="firstClass"){
                        for(var i=0; i<fare.length; i++){
                            fare[i].innerHTML = <%=flightDetail.getFirstClassFare() %>;
                        }
                    }

                });

            </script>
            <%
                }
            } else {
            %>
            <tr>
                <td colspan="10">
                    <p><%=message%></p>
                </td>
            </tr>
            <%
                }
            %>
            <tr>
                <td colspan="10">
                    <h5 style="color:orange;">Flights Available on Other Days</h5>
                </td>
            </tr>
            <%
                List<FlightDetails> altFlightDetails = (List<FlightDetails>) request.getAttribute("gotAltFlights");
                if (altFlightDetails.size() != 0) {
                    for (FlightDetails flightDetail : altFlightDetails) {
            %>
            <tr>
                <td><%=flightDetail.getCompany()%></td>
                <td><%=flightDetail.getOriginCity()%></td>
                <td><%=flightDetail.getDestinationCity()%></td>
                <td><%=flightDetail.getOriginAirport()%></td>
                <td><%=flightDetail.getDestinationAirport()%></td>
                <td><%=flightDetail.getDepartDate()%></td>
                <td><%=flightDetail.getDepartTime()%></td>
                <td><%=flightDetail.getArrivalTime()%></td>
                <td><span class="fare">5000</span></td>
                <td>
                    <form class="form-inline" action="bookTicket.do" id="myForm" method="post">
                        <input type="hidden" name="id"
                               value=<%=flightDetail.getFlightDetailsId()%> />
                        <%
                            request.setAttribute("flightDetails", flightDetail);
                        %>
                        <button type="submit" class="btn btn-outline-info" name="action"
                                value="bookTicket">Book</button>
                    </form>
                </td>
            </tr>
            <script type="text/javascript">
                const flightType=document.getElementById("classType");
                var fare = document.getElementsByClassName("fare");
                for(var i=0; i<fare.length; i++){
                    fare[i].innerHtml = <%=flightDetail.getEconomyFare() %>;
                }
                flightType.addEventListener("change",()=>{
                    if(flightType.value=="Economy"){
                        for(var i=0; i<fare.length; i++){
                            fare[i].innerHTML = <%=flightDetail.getEconomyFare() %>;
                        }
                    }
                    if(flightType.value=="Business"){
                        for(var i=0; i<fare.length; i++){
                            fare[i].innerHTML = <%=flightDetail.getBusinessFare() %>;
                        }


                    }
                    if(flightType.value=="firstClass"){
                        for(var i=0; i<fare.length; i++){
                            fare[i].innerHTML = <%=flightDetail.getFirstClassFare() %>;
                        }
                    }

                });

            </script>
            <%
                }
            } else {
            %>
            <tr>
                <td colspan="10">
                    <p><%=message%></p>
                </td>
            </tr>
            <%
                }
            %>
            </tbody>
        </table>
    </div>
</div>
<% FlightDao flightDao = new FlightDaoImp();
    HashMap<String,ArrayList< String>> flightDepart = flightDao.getSourceAndDest();
%>
<script>
    var City=new Array();
    <% for( String str : flightDepart.keySet()){ %>
    var destCity = new Array();
    <%
        for(String deststr : flightDepart.get(str)){
    %>
    destCity.push("<%=deststr%>");
    <%}%>
    City["<%=str%>"]=destCity;
    <%}%>

    var origin = document.getElementById("originData");
    console.log(origin);
    for(var index in City){
        const node = document.createElement("option");
        const textnode = document.createTextNode(index);
        node.appendChild(textnode);
        origin.appendChild(node);

    }
    console.log(City)
    const dest = document.getElementById("destData");
    const origininput = document.getElementById("origin");
    origininput.addEventListener("change",()=>{
        var val = origininput.value;
        dest.innerHTML="";
        console.log(val);
        for(var destval in City[val]){
            console.log(City[val][destval]);
            const node = document.createElement("option");
            const textnode = document.createTextNode(City[val][destval]);
            node.appendChild(textnode);
            dest.appendChild(node);
        }

    });
    const destInput = document.getElementById("destination");
    origininput.addEventListener("click",()=>{
        origininput.value="";
        destInput.value="";
    });
    destInput.addEventListener("click",()=>{
        destInput.value="";
    });
</script>



<%@ include file="footer.jsp"%>