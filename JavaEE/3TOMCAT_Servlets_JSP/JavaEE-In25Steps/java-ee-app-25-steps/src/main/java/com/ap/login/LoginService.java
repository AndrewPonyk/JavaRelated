package com.ap.login;

/**
 * Created by andrii on 27.06.17.
 */
public class LoginService {
    public boolean isUserValid(String user, String password) {
        if (user.equals("in28Minutes") && password.equals("dummy"))
            return true;

        return false;
    }
}
