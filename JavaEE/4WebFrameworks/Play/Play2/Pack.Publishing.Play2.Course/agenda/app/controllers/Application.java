package controllers;

import play.*;
import play.mvc.*;

import views.html.*;

public class Application extends Controller {

    public Result index() {

        com.avaje.ebean.Model m;

        return ok(index.render("gYour new application is ready тщеузфв ідея."));
    }
}