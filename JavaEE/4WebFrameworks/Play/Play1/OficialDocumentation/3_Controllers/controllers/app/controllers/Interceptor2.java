package controllers;

import play.mvc.Controller;
import play.mvc.Scope.Flash;
import play.mvc.With;
@With(Interceptor1.class)
public class Interceptor2  extends Controller{
	public static void index(){
		// flash - data from this scope vill be visible in NEXT , and only in NEXT request (useful thing)
		flash.put("temp", "tempValue");
		
		//System.out.println(flash.get("temp"));
		renderText("Interceptor2.index");
	}
}
