package apress.helloworld;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Created by andrii on 19.03.16.
 */
@WebServlet("/BasicAuthServlet")
public class BasicAuthServlet extends HttpServlet {
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        // user and password : dev dev
        String authHeader = request.getHeader("Authorization");
        if(authHeader != null && "Basic ZGV2OmRldg==".equals(authHeader)){
        	// dev:dev => ZGV2OmRldg==  (converted to Base64)
            response.getOutputStream().println("Successfully logged");
            response.setStatus(200);
            return;
        }

        response.setStatus(401);
        response.setHeader("WWW-Authenticate", "Basic realm='Atom bank'");
    }
}
