package app;

import org.apache.struts.action.*;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;

public class LogonAction extends Action {
    public boolean isUserLogon(String username,
                               String password) throws UserDirectoryException {

        return (UserDirectory.getInstance().isValidPassword(username, password));
    }

    public ActionForward execute(ActionMapping mapping,
                                 ActionForm form,
                                 HttpServletRequest request,
                                 HttpServletResponse response)
            throws IOException, ServletException {

        // Obtain username and password from web tier
        String username = ((LogonForm) form).getUsername();
        String password = ((LogonForm) form).getPassword();

        // Validate credentials with business tier
        boolean validated = false;
        try {

            validated = isUserLogon(username, password);
        } catch (UserDirectoryException ude) {
            // couldn't connect to user directory
            ActionErrors errors = new ActionErrors();
            errors.add("error.logon.connect",
                    new ActionMessage("error.logon.connect"));
            saveErrors(request, errors);
            // return to input page
            return (new ActionForward(mapping.getInput()));
        }

        if (!validated) {
            // credentials don't match
            ActionErrors errors = new ActionErrors();

            errors.add("name",
                    new ActionMessage("error.logon.invalid"));
            saveErrors(request, errors);
            // return to input page
            return (new ActionForward(mapping.getInput()));
        }

        // Save our logged-in user in the session,
        // because we use it again later.
        HttpSession session = request.getSession();
        session.setAttribute(Constants.USER_KEY, form);


        // Log this event, if appropriate
        StringBuffer message =
                new StringBuffer("LogonAction: User '");
        message.append(username);
        message.append("' logged on in session ");
        message.append(session.getId());
        servlet.log(message.toString());

        // Return success
        return (mapping.findForward(Constants.SUCCESS));

    }




}
