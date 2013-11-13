package controllers.mymodule;

import play.*;
import play.mvc.*;

import java.util.*;


public class Application extends Controller {

    public static void index() {
    	renderText("Hello from my module !!! ");
    }
}
