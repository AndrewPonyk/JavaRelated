package controllers;

import java.util.concurrent.atomic.AtomicInteger;

import jobs.LongJob;
import interceptors.SessionInterceptor;
import play.jobs.Job;
import play.libs.F;
import play.libs.F.Promise;
import play.mvc.Before;
import play.mvc.Controller;
import play.mvc.Scope.RenderArgs;
import play.mvc.With;
import play.mvc.results.RenderText;

@With(SessionInterceptor.class)
public class ControllerWithInterceptor extends Controller {
	
	@Before
	public static void markRequest(){
		System.out.println("CLASS BEFORE");
		System.out.println(RenderArgs.current().get("key"));
	}
	
	public static void index(){
		final AtomicInteger atomicInteger = new AtomicInteger(0);
		
		new Job(){
			public void doJob() throws Exception {
				Thread.sleep(2000);
				System.out.println("job1");
				atomicInteger.incrementAndGet();
			};
		}.now();
		
		new Job(){
			public void doJob() throws Exception {
				Thread.sleep(2000);
				System.out.println("job2");
				atomicInteger.incrementAndGet();
			};
		}.now();
		
		new Job(){
			public void doJob() throws Exception {
				Thread.sleep(2000);
				System.out.println("job3");
				atomicInteger.incrementAndGet();
			};
		}.now();
		
		
		renderArgs.put("resultI", atomicInteger.get());
		render();
	}
}
