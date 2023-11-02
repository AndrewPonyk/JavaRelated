package com.crossairlines.action;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.beanutils.BeanUtilsBean;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.actions.DispatchAction;

import com.crossairlines.dao.FlightDao;
import com.crossairlines.dao.UserDao;
import com.crossairlines.daoimplementation.FlightDaoImp;
import com.crossairlines.daoimplementation.UserDaoImp;
import com.crossairlines.form.UserForm;
import com.crossairlines.form.UserLoginForm;
import com.crossairlines.form.UserRegisterForm;
import com.crossairlines.pojo.User;

public class UserAction extends DispatchAction {
    UserDao userDao = new UserDaoImp();
    FlightDao flightDoa = new FlightDaoImp();

    public ActionForward register(ActionMapping mapping, ActionForm form, HttpServletRequest request,
                                  HttpServletResponse response) throws Exception {
        UserRegisterForm userRegForm = (UserRegisterForm) form;
        User userAuth = new User();
        BeanUtils.copyProperties(userAuth, userRegForm);
        System.out.println(userAuth);
        if (userDao.addUser(userAuth)) {
            return mapping.findForward("registerSuccess");
        } else {
            return mapping.findForward("registerFailed");
        }
    }

    public ActionForward login(ActionMapping mapping, ActionForm form, HttpServletRequest request,
                               HttpServletResponse response) throws Exception {
        UserLoginForm userlogForm = (UserLoginForm) form;
        User userAuth = new User();
        BeanUtils.copyProperties(userAuth, userlogForm);
        userAuth = userDao.verifyUser(userAuth);
        if (userAuth != null) {
            Cookie loginidcookie = new Cookie("loginidCookie", "" + userAuth.getUserAuthId());
            loginidcookie.setMaxAge(60);
            loginidcookie.setValue("" + userAuth.getUserAuthId());
            response.addCookie(loginidcookie);
            Cookie pwdcookie = new Cookie("pwdCookie", userAuth.getPassword());
            pwdcookie.setMaxAge(60);
            pwdcookie.setSecure(true);
            response.addCookie(pwdcookie);
            if(request.getSession().getAttribute("confirmFlight")!=null) {
                return mapping.findForward("redirectToBooking");
            }
            return mapping.findForward("LoginSuccess");
        } else {
            request.setAttribute("wrongAuth", "wrong.credentials");
            return mapping.findForward("LoginFailed");
        }
    }

    public void resources(HttpServletRequest request, HttpServletResponse response) {
        request.getSession().setAttribute("usersDetail", userDao.getAllUserDetails());
        request.getSession().setAttribute("allFlightDetails", flightDoa.getAllFlightDetails());
    }

    public ActionForward adminLogin(ActionMapping mapping, ActionForm form, HttpServletRequest request,
                                    HttpServletResponse response) throws Exception {
        UserForm userlogForm = (UserForm) form;
        User userAuth = new User();
        BeanUtilsBean.getInstance().getConvertUtils().register(false, false, 0);
        BeanUtils.copyProperties(userAuth, userlogForm);
        User userauth = userDao.verifyAdmin(userAuth);
        if (userauth != null) {
            Cookie loginidcookie = new Cookie("loginId", "" + userAuth.getUserAuthId());
            loginidcookie.setMaxAge(100);
            loginidcookie.setValue("" + userAuth.getUserAuthId());
            response.addCookie(loginidcookie);
            Cookie pwdcookie = new Cookie("pwdCookie", userAuth.getPassword());
            pwdcookie.setMaxAge(100);
            pwdcookie.setSecure(true);
            response.addCookie(pwdcookie);
            request.getSession().setAttribute("userDetail", userauth);
            resources(request, response);

            return mapping.findForward("AdminLoginSuccess");
        } else {
            request.setAttribute("wrongAuth", "wrong.credentials");
            return mapping.findForward("AdminLoginFailed");
        }
    }

    public ActionForward updateUserDetails(ActionMapping mapping, ActionForm form, HttpServletRequest request,
                                           HttpServletResponse response) throws Exception {
        UserLoginForm userLoginForm = (UserLoginForm) form;
        System.out.println(userLoginForm);
        User user = new User();
        BeanUtils.copyProperties(user, userLoginForm);
        System.out.println(user);
        userDao.updateUserDetails(user);
        resources(request, response);
        return mapping.findForward("adminUpdatedUser");
    }

    public ActionForward update(ActionMapping mapping, ActionForm form, HttpServletRequest request,
                                HttpServletResponse response) throws Exception {
        int id = Integer.parseInt(request.getParameter("userAuthId"));
        User user = userDao.getUserDetails(id);
        if (user != null) {
            request.setAttribute("user", user);
            resources(request, response);
            return mapping.findForward("updateUser");
        }
        return mapping.findForward("usersDetails");
    }

    public ActionForward flights(ActionMapping mapping, ActionForm form, HttpServletRequest request,
                                 HttpServletResponse response) throws Exception {
        Cookie[] cookies = request.getCookies();
        cookies[0].setMaxAge(0);
        cookies[1].setMaxAge(0);
        return mapping.findForward("logoutAdmin");
    }

    public ActionForward delete(ActionMapping mapping, ActionForm form, HttpServletRequest request,
                                HttpServletResponse response) throws Exception {
        UserLoginForm userform = (UserLoginForm) form;
        User user = new User();
        BeanUtils.copyProperties(user, userform);
        userDao.deleteUser(user);
        resources(request, response);
        return mapping.findForward("userDeleted");
    }

    public ActionForward addNewUser(ActionMapping mapping, ActionForm form, HttpServletRequest request,
                                    HttpServletResponse response) throws Exception {
        UserForm userform = (UserForm) form;
        User user = new User();
        BeanUtils.copyProperties(user, userform);
        System.out.println(userform);
        System.out.println(user);
        userDao.addUser(user);
        resources(request, response);
        return mapping.findForward("userAdded");
    }

}