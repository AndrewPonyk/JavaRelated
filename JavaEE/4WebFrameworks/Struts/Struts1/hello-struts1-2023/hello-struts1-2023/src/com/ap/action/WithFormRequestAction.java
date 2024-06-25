package com.ap.action;

import com.ap.form.WithFormRequestForm;
import org.apache.struts.action.Action;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class WithFormRequestAction extends Action {
    @Override
    public ActionForward execute(ActionMapping mapping, ActionForm form, HttpServletRequest request, HttpServletResponse response) throws Exception {
        WithFormRequestForm form1 = (WithFormRequestForm) form;
        form1.setMessage("Hello World!");

        return mapping.findForward("success");
    }
}
