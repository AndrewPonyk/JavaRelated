package interceptors;

import java.util.concurrent.atomic.AtomicInteger;

import play.mvc.Before;
import play.mvc.Controller;
import play.mvc.Scope.RenderArgs;

public class SessionInterceptor  extends Controller{
	

	
	@Before
	public static void sessionCheck(){
		//System.out.println("INTERCEPT");
		//session.put("key", "value");
		//System.out.println(session.get("key"));
		

		RenderArgs.current().put("key", Math.random()+" ");
		System.out.println(RenderArgs.current().get("key"));
	}
}
