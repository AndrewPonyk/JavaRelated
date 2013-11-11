package controllers;

import play.*;
import play.mvc.*;

import java.util.*;

import models.*;

public class Application extends Controller {

    public static void index() {
		session.put("username", "andrew9999");
		render();
    }
    
    
    public static void process(String login){
    	/*So for example:

    		public static destroyMyAccount() {
    		    checkAuthenticity();
    		    …
    		}
    		Will only work when called from a form including a proper authenticity token:

    		<form method="post" action="/account/destroy">
    		    #{authenticityToken /}
    		    <input type="submit" value="destroy my account">
    		</form>*/
    	
    	checkAuthenticity();
    

    	
    if(true){
    		renderText("Ok , your request is good =) , your login : " +login );
    	}else{
    		renderText("Stop !");
    	}
    	
    	
    }
}