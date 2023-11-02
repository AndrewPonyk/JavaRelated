package app;

import org.apache.struts.action.Action;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

public class LogoffAction extends Action {
    @Override
    public ActionForward execute(ActionMapping mapping, ActionForm form, HttpServletRequest request, HttpServletResponse response) throws Exception {
        // Extract attributes we will need
        HttpSession session = request.getSession();
        LogonForm user = (LogonForm)
                session.getAttribute(Constants.USER_KEY);

        // Log this user logoff
        StringBuffer message =
                new StringBuffer("LogoffAction: User '");
        if (user != null) {
            message.append(user.getUsername());
            message.append("' logged off in session ");
        }
        message.append(session.getId());
        servlet.log(message.toString());

        // Remove user login
        session.removeAttribute(Constants.USER_KEY);

        // Return success
        return (mapping.findForward(Constants.SUCCESS));
    }
}
