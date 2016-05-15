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
@WebServlet("/DigestAuthServlet")
public class DigestAuthServlet extends HttpServlet {
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        // user and password : adm adm
        String authHeader = request.getHeader("Authorization");
        // in crhome it works ffce43a2d7ef7d0090f8e119b5f5373c, if firefox i have another responce
        if(authHeader != null && authHeader.contains("ffce43a2d7ef7d0090f8e119b5f5373c")){
        	//adm:adm => (using http digest) => Digest username="adm", realm="Atom bank\" qop=\"auth,auth-int", nonce="123", uri="/chap2_firstServletAndJsp/DigestAuthServlet", response="ffce43a2d7ef7d0090f8e119b5f5373c", opaque="456"
        	response.getOutputStream().println("Successfully logged");
            response.setStatus(200);
            return;
        }

        response.setStatus(401);
        response.setHeader("WWW-Authenticate", "Digest realm=\"Atom bank\""
        		+ " qop=\"auth,auth-int\","
        		+ "nonce=\"123\","
        		+ "opaque=\"456\"");
    }
}
