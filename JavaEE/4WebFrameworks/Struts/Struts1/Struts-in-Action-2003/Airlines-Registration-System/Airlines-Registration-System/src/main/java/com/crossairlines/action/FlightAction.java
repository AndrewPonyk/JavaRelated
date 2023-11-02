package com.crossairlines.action;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.beanutils.BeanUtils;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.actions.DispatchAction;

import com.crossairlines.dao.FlightDao;
import com.crossairlines.dao.UserDao;
import com.crossairlines.daoimplementation.FlightDaoImp;
import com.crossairlines.daoimplementation.UserDaoImp;
import com.crossairlines.form.BookTicketForm;
import com.crossairlines.form.FlightDetailForm;
import com.crossairlines.form.PaymentForm;
import com.crossairlines.pojo.BookTicket;
import com.crossairlines.pojo.FlightDetails;
import com.crossairlines.pojo.Payment;
import com.crossairlines.pojo.User;

public class FlightAction extends DispatchAction{

    FlightDao flightDao = new FlightDaoImp();
    public ActionForward addFlight(ActionMapping mapping, ActionForm form, HttpServletRequest request,
                                   HttpServletResponse response) throws Exception {
        FlightDetailForm flightForm = (FlightDetailForm) form;
        System.out.println(flightForm);
        FlightDetails fd = new FlightDetails();
        BeanUtils.copyProperties(fd, flightForm);
        System.out.println(fd);
        flightDao.addFlight(fd);
        return mapping.findForward("flightAdded");
    }

    public ActionForward bookTicket(ActionMapping mapping, ActionForm form, HttpServletRequest request,
                                    HttpServletResponse response) throws Exception {
        BookTicketForm btf = (BookTicketForm) form;
        System.out.println("Book Ticket : "+btf);

        FlightDetails flightDetails = flightDao.getFlightDetail(btf.getId());
        request.getSession().setAttribute("confirmFlight",flightDetails);
        Cookie[] cookies = request.getCookies();
        for(Cookie ck : cookies) {
            if(ck.getName().equals("loginidCookie") && ck.getValue()!=null) {
                return mapping.findForward("bookMyTicket");
            }
        }
        return mapping.findForward("redirectToLogin");
    }

    public ActionForward checkout (ActionMapping mapping, ActionForm form, HttpServletRequest request,
                                   HttpServletResponse response) throws Exception {
        BookTicketForm btf = (BookTicketForm) form;
        System.out.println("Book Ticket : "+btf);
        request.getSession().setAttribute("bookingTicket", btf);

        return mapping.findForward("checkout");
    }

    public ActionForward payment (ActionMapping mapping, ActionForm form, HttpServletRequest request,
                                  HttpServletResponse response) throws Exception {
        PaymentForm paymentForm = (PaymentForm) form;
        BookTicketForm btr = (BookTicketForm) request.getSession().getAttribute("bookingTicket");
        FlightDetails flightDetails = (FlightDetails) request.getSession().getAttribute("confirmFlight");

        BookTicket bookTicket = new BookTicket();
        Payment payment = new Payment();
        BeanUtils.copyProperties(bookTicket, btr);
        BeanUtils.copyProperties(payment, paymentForm);

        int id=0;
        for(Cookie cookie:request.getCookies()) {
            if(cookie.getName().equals("loginidCookie")) {
                id = Integer.parseInt(cookie.getValue());
                System.out.println(id);
            }
        }
        System.out.println(id);
        User user = new UserDaoImp().getUserDetails(id);
        payment.setUser(user);
        bookTicket.setUser(user);
        payment.setBookTicket(bookTicket);
        bookTicket.setPayment(payment);
        bookTicket.setFlightDetails(flightDetails);
        flightDao.confirmBooking(bookTicket, payment);
        return mapping.findForward("payed");
    }


}