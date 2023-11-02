<%@page import="com.crossairlines.pojo.FlightDetails"%>
<%@ include file="header.jsp"%>

<div class="masthead d-flex justify-content-center align-items-center">
    <div class="container-fluid">
        <div class="">
            <%
                FlightDetails flightDetails = (FlightDetails) request.getSession().getAttribute("confirmFlight");
            %>
            <div class="booking-form">
                <form action="payment.do" method="post" onsubmit="setFinalFare()">
                    <div class="row">
                        <h3 class="text-white text-center">Booking Preview</h3>
                    </div>
                    <div class="row">
                        <div class="col-4">
                            <div class="form-group">
                                <span class="form-label">Flying from</span>
                                <div>
                                    <input class="form-control  text-secondary" id="origin"
                                           name="originCity" type="text" required placeholder="Origin City"
                                           value="<%=flightDetails.getOriginCity()%>" disabled
                                           autoComplete="on" list="originData">
                                </div>
                            </div>
                        </div>
                        <div class="col-4">
                            <div class="form-group">
                                <span class="form-label">Flyning to</span>
                                <div>

                                    <input class="form-control text-secondary" type="text"
                                           id="destinationCity" list="destData" autoComplete="on"
                                           name="destination"
                                           value="<%=flightDetails.getDestinationCity()%>" disabled
                                           placeholder="City">

                                </div>

                            </div>
                        </div>
                        <div class="col-md-2">
                            <div class="form-group">
                                <span class="form-label ">Departure Date</span> <input
                                    class="form-control text-secondary" name="departDate"
                                    value="<%=flightDetails.getDepartDate()%>"
                                    pattern="d{2}-d{2}-d{2}" disabled type="date" id="set_bdate"
                                    required>
                            </div>
                        </div>
                        <div class="col-md-2">
                            <div class="form-group">
                                <span class="form-label">Arrival Date</span> <input
                                    class="form-control text-secondary" id="arrivalDate"
                                    pattern="d{2}-d{2}-d{2}"
                                    value="<%=flightDetails.getArrivalDate()%>" disabled
                                    name="arrivalDate" type="date">
                            </div>
                        </div>
                    </div>
                    <div class="row">
                        <div class="col-4">
                            <div class="form-group">
                                <span class="form-label">Flying from Ariport</span>
                                <div>

                                    <input class="form-control text-secondary" id="origin"
                                           name="originAirport" type="text" required placeholder="Origin City"
                                           value="<%=flightDetails.getOriginAirport()%>" disabled
                                           autoComplete="on" list="originData">
                                </div>
                            </div>
                        </div>
                        <div class="col-4">
                            <div class="form-group">
                                <span class="form-label">Flyning to Airport</span>
                                <div>
                                    <input class="form-control text-secondary" type="text"
                                           id="destinationAirport" list="destData" autoComplete="on" disabled
                                           name="destinationAirport"
                                           value="<%=flightDetails.getDestinationAirport()%>"
                                           placeholder="City">
                                </div>

                            </div>
                        </div>
                        <div class="col-md-2">
                            <div class="form-group">
                                <span class="form-label">Departure Time</span> <input
                                    class="form-control text-secondary" name="departTime"
                                    type="time" disabled id="set_bdate"
                                    value="<%=flightDetails.getDepartTime()%>" required>
                            </div>
                        </div>
                        <div class="col-md-2">
                            <div class="form-group">
                                <span class="form-label">Arrival time</span> <input
                                    class="form-control text-secondary" id="arrivalTime"
                                    value="<%=flightDetails.getArrivalTime()%>" disabled
                                    name="arrivalTime" type="time">
                            </div>
                        </div>

                    </div>
                    <div class="row">
                        <div class="col">
                            <div class="form-group">
                                <span class="form-label">Company </span> <input
                                    class="form-control text-secondary" id="company"
                                    value="<%=flightDetails.getCompany()%>" disabled name="company"
                                    type="text">
                            </div>
                        </div>
                        <div class="col">
                            <span class="form-label">Class Type</span> <select
                                class="form-select form-control" id="classType" form="myForm">
                            <option value="Economy" class="form-control">Economy</option>
                            <option value="Business" class="form-control">Business</option>
                            <option value="firstClass" class="form-control">First
                                Class</option>
                        </select>
                            <input type="hidden" name="classType" id="classTypeHide">
                        </div>

                        <div class="col">
                            <div class="form-group">
                                <span class="form-label">Adults </span> <input
                                    class="form-control" id="adult" value="1" name="adultCount"
                                    min=1 max=6 type="number">
                            </div>
                        </div>
                        <div class="col">
                            <div class="form-group">
                                <span class="form-label">Children </span> <input
                                    class="form-control" min=0 id="child" value="0"
                                    name="childCount" type="number">
                            </div>
                        </div>
                        <div class="col">
                            <div class="form-group">
                                <span class="form-label">Fare </span> <input
                                    class="form-control" id="finalFareInput"
                                    name="finalFareInput"
                                    value="1"
                                    type="number" disabled>
                                <input type="hidden" name="finalFare" id="finalFare">
                            </div>
                        </div>

                    </div>
                    <div class="form-btn col">
                        <button type="submit" name="action" value="checkout"
                                class="submit-btn">Proceed to Check out</button>
                    </div>

                </form>
            </div>
        </div>
    </div>
</div>
<script type="text/javascript">
    const classType= document.getElementById("classType");
    const adult = document.getElementById("adult");
    const child = document.getElementById("child");
    const fare = document.getElementById("finalFareInput");
    var fareval=<%=flightDetails.getEconomyFare()%>;
    classType.addEventListener("change",()=>{
        adult.value=1;
        child.value=0;
        if(classType.value=="Economy"){
            fareval=<%=flightDetails.getEconomyFare()%>;
            fare.value=fareval;
        }
        else if(classType.value == "Business"){
            fareval=<%=flightDetails.getBusinessFare()%>;
            fare.value=fareval;
        }
        else if(classType.value=="firstClass"){
            fareval=<%=flightDetails.getFirstClassFare()%>;
            fare.value=fareval;
        }
    });
    var adultfare=0;
    adult.addEventListener("change",()=>{
        fare.value=fareval*adult.value;
        adultfare = fare.value;
        child.value=0;
        child.setAttribute("max",(adult.value-1))
    });
    child.addEventListener("change",()=>{
        childfare = fareval*child.value/2;
        fare.value= parseInt(adultfare)+parseInt(childfare);
    });

    function setFinalFare(){
        document.getElementById("finalFare").value=fare.value;
        document.getElementById("classTypeHide").value=classType.value;
    }
</script>

<%@ include file="footer.jsp"%>