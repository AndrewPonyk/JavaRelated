<%@page import="com.crossairlines.pojo.FlightDetails"%>
<%@page import="java.util.List"%>
<%@ include file="adminHeader.jsp"%>

<div class="masthead">
    <div class="p-5 h-100 d-flex justify-content-center align-items-center">
        <div class="text-white  w-100 ">
            <p class="text-center" style="font-size: 25px;">Manage Flights</p>
            <div class="">
                <form action="flightadded.do" method="post">
                    <div class="form-group row">
                        <div class="col-6  row">
                            <input type="radio" id="domestic" class="col"
                                   name="destinationType" required> <label for="domestic"
                                                                           class="col">Domestic</label>
                        </div>
                        <div class="col-6 row">
                            <input type="radio" required class="col" id="international"
                                   name="destinationType"> <label for="international"
                                                                  class="col">International</label>
                        </div>
                    </div>
                    <div class="form-group row">
                        <div class="col-6 row">
                            <label for="fromcountry" class="col">From Country : </label> <input
                                type="text" required name="fromCountry" class="col"
                                id="fromcountry" placeholder="Country">
                        </div>
                        <div class="col-6 row">
                            <label for="destCountry" class="col">To Country: </label> <input
                                type="text" name="destCountry" required class="col"
                                id="destCountry" placeholder="Country">
                        </div>
                    </div>
                    <div class="form-group row">
                        <div class="col-6 row">

                            <label for="fromcity" class="col">From city : </label> <input
                                type="text" name="originCity" required id="fromcity" class="col"
                                placeholder="From City">
                        </div>
                        <div class="col-6 row">
                            <label for="destcity" class="col">To city : </label> <input
                                type="text" name="destinationCity" required id="destcity"
                                class="col" placeholder="To City">
                        </div>
                    </div>
                    <div class="form-group row">
                        <div class="col-6 row">
                            <label for="originAirport" class="col">Origin Airport : </label>
                            <input type="text" required name="originAirport"
                                   id="originAirport" class="col" placeholder="Origin Airport">
                        </div>
                        <div class="col-6 row">
                            <label for="destAirport" class="col">Destination Airport
                                : </label> <input required type="text" name="destinationAirport"
                                                  id="destAirport" class="col" placeholder="Destination Airport">
                        </div>
                    </div>

                    <div class="form-group row">
                        <div class="col-6 row">
                            <label for="departDate" class="col">Departure Date : </label> <input
                                type="date" required name="departDatet" id="departDate"
                                pattern="d{4}-d{2}-d{2}" class="col"
                                placeholder="Departure Date">
                        </div>
                        <div class="col-6 row">
                            <label for="arrivalDate" class="col">Arrival Date : </label> <input
                                type="date" required name="arrivalDatet"
                                pattern="d{4}-d{2}-d{2}" id="arrivalDate" class="col"
                                placeholder="Arrival Date">
                        </div>
                    </div>
                    <div class="form-group row">
                        <div class="col-6 row">
                            <label for="departTime" class="col">Departure Time: </label> <input
                                type="time" required name="departTime" id="departTime"
                                class="col" placeholder="Departure Time">
                        </div>
                        <div class="col-6 row">
                            <label for="arrivalTime" class="col">Arrival Time : </label> <input
                                type="time" required name="arrivalTime" id="arrivalTime"
                                class="col" placeholder="Arrival Time">
                        </div>
                    </div>

                    <div class="form-group row">
                        <div class="col-6 row">
                            <label for="company" class="col">Company </label> <input
                                type="text" required name="company" id="company" class="col"
                                placeholder="Company">
                        </div>
                        <div class="col-6 row">
                            <label for="fligtType" class="col">Flight Type : </label> <select
                                name="flightType" required class="col" id="flightType">
                            <option value="Non Stop">Non Stop</option>
                            <option value="Multi Stop">Multi Stop</option>
                            <option value="One Stop">One Stop</option>
                        </select>

                        </div>
                    </div>


                    <div class="form-group row">
                        <div class="col-4 row">
                            <label for="economyFare" class="col">Economy Fare: </label> <input
                                type="number" required name="economyFare" id="economyFare"
                                class="col" placeholder="Economy Fare">
                        </div>
                        <div class="col-4 row">
                            <label for="businessFare" class="col">Business Fare: </label> <input
                                type="number" required name="businessFare" id="businessFare"
                                class="col" placeholder="Business Fare">
                        </div>
                        <div class="col-4 row">
                            <label for="firstClassFare" class="col"> Firt Class Fare:
                            </label> <input type="number" required name="firstClassFare"
                                            id="firstClassFare" class="col" placeholder="Firt Class Fare">
                        </div>
                    </div>
                    <div class="form-group">
                        <button type="submit" class="btn btn-outline-success float-right"
                                name="action" value="addFlight">Add Flight</button>
                    </div>
                </form>
            </div>
        </div>
    </div>
</div>
<div class="masthead">

</div>


<%@ include file="footer.jsp"%>