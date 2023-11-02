<%@page import="com.crossairlines.form.BookTicketForm"%>
<%@ include file="header.jsp"%>
<div class="masthead">
    <%
        BookTicketForm btf = (BookTicketForm) request.getSession().getAttribute("bookingTicket");
    %>
    <div
            class="container h-100 d-flex justify-content-center align-items-center">
        <div class="container row text-white">
            <div class="col-6"></div>
            <form class="form col-6" action="printTicket.do">
                <div class="form-group row">
                    <label>Card Number :</label> <input class="form-control"
                                                        name="cardNumber" id="cardNumber" placeholder="Card Number" type="number" min=100000000000
                                                        max=999999999999>
                </div>
                <div class="form-group row">
                    <label>Name on Card </label> <input class="form-control"
                                                        name="cardHolderName" id="cardHolderName" placeholder="Name on Card" type="text" >
                </div>
                <div class="form-group row">
                    <div class="col">
                        <label>Expiry Date</label> <input class="form-control"
                                                          name="expiryDate" id="expiryDate" type="date" >
                    </div>
                    <div class="col">
                        <label>CVV number </label>
                        <input type="number" class="form-control" placeholder="cvv" name="cvv" min=100 max=999 id="cvv">
                    </div>
                </div>
                <div class="form-group row">
                    <input type="hidden" name="amount" value=<%=btf.getFinalFare() %>/>
                    <button class="btn btn-outline-success" name="action" value="payment">Pay <%=btf.getFinalFare() %></button>
                </div>
            </form>
        </div>
    </div>
</div>

<%@ include file="footer.jsp"%>