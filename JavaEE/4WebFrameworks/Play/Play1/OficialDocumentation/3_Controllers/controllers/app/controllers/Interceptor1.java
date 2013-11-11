package controllers;

import play.mvc.Before;
import play.mvc.Catch;
import play.mvc.Controller;

public class Interceptor1 extends Controller {
	
	//@Before(unless={"index"}) - interceptor will be invoked for all actions , but not for index
	@Before
	public static void beforeInterceptor1(){
		System.out.println("Before Interceptor1");
	}
	
	@Catch({NullPointerException.class})
	public static void catchNullPointer(Throwable throwable){
		System.out.println("Catch null pointer interceptor in Interceptor1");
	}
	
	public static void index(){
		renderText("Interceptor1.index");
	}
	
	public static void throwNullPointer(){

			String nul=null;
			//System.out.println(nul.toString());
			int a=nul.length();
	
		renderText("Throwing null pointer");
	}
	
}
