package com.crossairlines.navigator;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.struts.action.Action;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.actions.DispatchAction;

import com.crossairlines.pojo.FlightDetails;

public class Navigator extends Action {
    public ActionForward execute(ActionMapping mapping, ActionForm form, HttpServletRequest request,
                                 HttpServletResponse response) throws Exception {
        Cookie[] cookies = request.getCookies();
        for(Cookie ck:cookies) {
            if(ck.getName() == "loginid") {
                ck.setValue("0");
                ck.setMaxAge(0);
            }
        }
        response.addCookie(cookies[0]);
        System.out.println("logout");
        return mapping.findForward("logout");

    }
    public ActionForward register(ActionMapping mapping, ActionForm form, HttpServletRequest request,
                                  HttpServletResponse response) throws Exception {
        return mapping.findForward("registerpage");
    }
    public ActionForward bookTicket(ActionMapping mapping, ActionForm form, HttpServletRequest request,
                                    HttpServletResponse response) throws Exception {
        userForm uform = (userForm) form;
        int id = uform.getId();

        return mapping.findForward("bookTicket");
    }



}