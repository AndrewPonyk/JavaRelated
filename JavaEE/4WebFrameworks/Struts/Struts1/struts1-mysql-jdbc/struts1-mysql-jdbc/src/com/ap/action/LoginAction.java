package com.ap.action;

import com.ap.dao.Ifacelogin;
import com.ap.dao.ImplLoginImpl;
import com.ap.form.LoginForm;
import org.apache.struts.action.Action;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.Map;

public class LoginAction extends Action {
    @Override
    public ActionForward execute(ActionMapping mapping, ActionForm form,
                                 HttpServletRequest request, HttpServletResponse response) throws Exception {
        LoginForm login = (LoginForm) form;
        Ifacelogin iface = new ImplLoginImpl();

        if (iface.validateLogin(login) != null) {
            return mapping.findForward("success");
        } else {
            Map<String, Object> model = new HashMap<>();
            model.put("message", "LOGIN AND PASSWORD do not match with DB!");
            request.setAttribute("model", model);

            return mapping.findForward("error");
        }
    }
}
