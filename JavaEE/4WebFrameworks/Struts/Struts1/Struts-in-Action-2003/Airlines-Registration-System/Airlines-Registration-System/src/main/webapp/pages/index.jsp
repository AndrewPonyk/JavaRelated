<%@page import="java.util.HashMap"%>
<%@page import="com.crossairlines.daoimplementation.FlightDaoImp"%>
<%@page import="com.crossairlines.dao.FlightDao"%>
<%@page import="java.util.ArrayList"%>
<%@ include file="header.jsp"%>

<title>Home Page</title>
<% FlightDao flightDao = new FlightDaoImp();
    HashMap<String,ArrayList< String>> flightDepart = flightDao.getSourceAndDest();
%>
<header class="masthead">
    <div
            class="container px-4 px-lg-5 d-flex h-100 align-items-center justify-content-center">
        <div class="d-flex justify-content-center">
            <div class="text-center">
                <h1 class="mx-auto my-0 text-uppercase">cross AirLines</h1>
                <h2 class="text-white-50 mx-auto mt-2 mb-5">Traveling the
                    globe, making dreams come true.</h2>
                <a class="btn btn-primary" href="#about">Get Started</a>
            </div>
        </div>
    </div>
</header>

<section class="about-section text-center" id="about">
    <div class="container px-4 px-lg-5">
        <div class="row gx-4 gx-lg-5 justify-content-center">
            <div class="col-lg-8">
                <h2 class="text-white mb-4">Flight Search</h2>
                <p class="text-white-50">
                    The project Airline reservation system is used for maintaining the
                    Airlines information's, Flight information, Passenger information,
                    Booking/Reservations, Cancellation. <a href="#search">Search
                    Flights for BookIng Ticket</a>
                </p>
            </div>
        </div>
        <img class="img-fluid"
             src="<%=request.getContextPath()%>/assets/images/airplane2.jpg"
             alt="..." />
    </div>
</section>
<!-- Projects-->
<section class="bg-dark" id="search">
    <div class="">
        <div id="booking" class="section">
            <div class="section-center">
                <div class="container">
                    <div class="row">
                        <div class="booking-form">
                            <form action="searchFlight.do" method="post">
                                <div class="form-group">
                                    <div class="form-checkbox">
                                        <label for="one-way"> <input type="radio" id="one-way"
                                                                     value="one Way" name="flightType"> <span></span>One
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

                                                <input class="form-control" id="origin" name="origin" type="text"
                                                       required placeholder="Origin City" autoComplete="on"
                                                       list="originData">
                                            </div>
                                        </div>
                                    </div>
                                    <div class="col-3">
                                        <div class="form-group">
                                            <span class="form-label">Flyning to</span>
                                            <div>

                                                <input class="form-control" type="text" id="destination" list="destData"
                                                       autoComplete="on" name="destination" required
                                                       placeholder="City">

                                            </div>

                                        </div>
                                    </div>
                                    <div class="col-md-3">
                                        <div class="form-group">
                                            <span class="form-label">Departing</span> <input
                                                class="form-control" name="departDate" type="date" id="set_bdate" required>
                                        </div>
                                    </div>
                                    <div class="col-md-3">
                                        <div class="form-group">
                                            <span class="form-label">Returning</span> <input
                                                class="form-control" id="returnDate" name="returnDate"
                                                type="date">
                                        </div>
                                    </div>
                                    <div class="row">
                                    </div>

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
<!-- Signup-->
<section class="signup-section" id="signup">
    <div class="container px-4 px-lg-5">
        <div class="row gx-4 gx-lg-5">
            <div class="col-md-10 col-lg-8 mx-auto text-center">
                <i class="far fa-paper-plane fa-2x mb-2 text-white"></i>
                <h2 class="text-white mb-5">Subscribe to receive updates! On
                    Cross Airline</h2>

                <form class="form-signup" id="contactForm"
                      data-sb-form-api-token="API_TOKEN">
                    <!-- Email address input-->
                    <div class="row input-group-newsletter">
                        <div class="col">
                            <input class="form-control" id="emailAddress" type="email"
                                   placeholder="Enter email address..."
                                   aria-label="Enter email address..."
                                   data-sb-validations="required,email" />
                        </div>
                        <div class="col-auto">
                            <input class="btn btn-primary" id="submitButton" type="submit"
                                   value="Notify Me">
                        </div>
                    </div>
                    <div class="invalid-feedback mt-2"
                         data-sb-feedback="emailAddress:required">An email is
                        required.</div>
                    <div class="invalid-feedback mt-2"
                         data-sb-feedback="emailAddress:email">Email is not valid.</div>
                    <!-- Submit success message-->
                    <!---->
                    <!-- This is what your users will see when the form-->
                    <!-- has successfully submitted-->
                    <div class="d-none" id="submitSuccessMessage">
                        <div class="text-center mb-3 mt-2 text-white">
                            <div class="fw-bolder">Form submission successful!</div>
                            To activate this form, sign up <br />
                        </div>
                    </div>
                    <!-- Submit error message-->
                    <!---->
                    <!-- This is what your users will see when there is-->
                    <!-- an error submitting the form-->
                    <div class="d-none" id="submitErrorMessage">
                        <div class="text-center text-danger mb-3 mt-2">Error sending
                            message!</div>
                    </div>
                </form>
            </div>
        </div>
    </div>
</section>
<!-- Contact-->
<section class="contact-section bg-black">
    <div class="container px-4 px-lg-5">
        <div class="row gx-4 gx-lg-5">
            <div class="col-md-4 mb-3 mb-md-0">
                <div class="card py-4 h-100">
                    <div class="card-body text-center">
                        <i class="fas fa-map-marked-alt text-primary mb-2"></i>
                        <h4 class="text-uppercase m-0">Address</h4>
                        <hr class="my-4 mx-auto" />
                        <div class="small text-black-50">
                            Antela 10<sup>th</sup> Floor, Cross Airlines, Goregoan
                        </div>
                    </div>
                </div>
            </div>
            <div class="col-md-4 mb-3 mb-md-0">
                <div class="card py-4 h-100">
                    <div class="card-body text-center">
                        <i class="fas fa-envelope text-primary mb-2"></i>
                        <h4 class="text-uppercase m-0">Email</h4>
                        <hr class="my-4 mx-auto" />
                        <div class="small text-black-50">
                            <a href="#!">support@crossairline.com</a>
                        </div>
                    </div>
                </div>
            </div>
            <div class="col-md-4 mb-3 mb-md-0">
                <div class="card py-4 h-100">
                    <div class="card-body text-center">
                        <i class="fas fa-mobile-alt text-primary mb-2"></i>
                        <h4 class="text-uppercase m-0">Phone</h4>
                        <hr class="my-4 mx-auto" />
                        <div class="small text-black-50">+91 9324621622</div>
                    </div>
                </div>
            </div>
        </div>
        <div class="social d-flex justify-content-center">
            <a class="mx-2" href="#!"><i class="fab fa-twitter"></i></a> <a
                class="mx-2" href="#!"><i class="fab fa-facebook-f"></i></a> <a
                class="mx-2" href="#!"><i class="fab fa-github"></i></a>
        </div>
    </div>
</section>
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

<%@ include file="footer.jsp" %>