package app;

import org.apache.struts.action.ActionErrors;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.action.ActionMessage;

import javax.servlet.http.HttpServletRequest;

public class LogonForm extends ActionForm {
    /**
     * The password.
     */
    private String password = null;

    /**
     * The username.
     */



    public LogonForm(){
        int i = 0;
    }
    private String username = null;

    public void reset(ActionMapping mapping,
                      HttpServletRequest request) {

        setPassword(null);
        setUsername(null);

    }



    public ActionErrors validate(ActionMapping mapping,
                                 HttpServletRequest request) {

        ActionErrors errors = new ActionErrors();

        if ((username == null) || (username.length() < 1))
            errors.add("username",
                    new ActionMessage("error.username.required"));

        if ((password == null) || (password.length() < 1))
            errors.add("password",
                    new ActionMessage("error.password.required"));

        return errors;

    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }
}
