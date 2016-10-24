package spring.aspect;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

import spring.bean.UserManager;

@Aspect
@Component
public class AspectManager {

	boolean loggingRequired = true;
	
	@Before("execution(* spring.bean.MailService.send*(*))")
	public void monitorMailServiceSendMethod() {
		//Before Advice: It is a code that executes before a joint point and cannot change the execution flow unless there is an exception inside that advice
		System.out.println("@Aspect @Before MailService sendMessage(String message) method is called");					
	}
	
	@After("execution(* spring.bean.RegisterService.register*(*))")
	public void monitorRegisterServiceRegisterMethod() {
		System.out.println("@Aspect @After RegisterService registerUser(User user) method is called");					
	}
	
	@AfterThrowing(value="execution(void spring.bean.UserManager.throw*())",throwing="e")
	public void afterThrowingUserManagerThrow(JoinPoint jp , Exception e) {
		//After Throwing Advice: Advice to be executed after target object's method throws an exception
		System.out.println("@Aspect @AfterThrowing UserManager throwUserUpdateExceptionMethod method is called");	
		System.out.println("Target Class: " + jp.getClass());
		System.out.println("Target Kind: " + jp.getKind());
		System.out.println("Target Signature: " + jp.getSignature());
		System.out.println("Target Target: " + jp.getTarget());
		UserManager userManager = (UserManager)jp.getTarget();				
		//comment out
		//e.printStackTrace();
	}
	
	@AfterReturning(value="execution(boolean spring.bean.UserManager.throw*())",returning="result")
	public void afterReturningUserManagerThrow(JoinPoint jp, boolean result) {
		//After Returning Advice: Advice to be executed after successful return from a method a call inside target object
		System.out.println("@Aspect @AfterReturning UserManager throwUserUpdateExceptionMethod method is called");		
		System.out.println("result: " + result);
	}
	
	@After("execution(void spring.bean.UserManager.throw*())") 
	public void afterThrowUserUpdateExceptionMethod() {	
		//After(finally) Advice: Advice to be executed even if the target object's method throws an exception or not
		System.out.println("@Aspect @After UserManager throwUserUpdateExceptionMethod method is called");		
	}
		
	@Around("execution(boolean spring.bean.UserManager.deleteUser(*))") 
	public boolean aroundDeleteUser(ProceedingJoinPoint point) throws Throwable {	
		System.out.println("@Aspect @Around UserManager deleteUser(User user) method before proceed() is called");	
		
		if(loggingRequired == true){
			System.out.println("AspectManager aroundDeleteUser log active");
			point.proceed();
		}else {
			point.proceed();
		}
		//Around Advice: Advice to be executed before of after the target object's method run and able to change the execution flow using the proceed method in ProceedingJointPoint parameter
		System.out.println("@Aspect @Around UserManager deleteUser(User user) method after proceed() is called");	
		return true;
	}
	
	
	//named pointcut example
	@After("userManagerMethods() || registerServiceMethods()") 
	public void namedPointCutExample(JoinPoint jp) {
		System.out.println(" ***** namePointCut ***** " + jp.getSignature() + " is called");
	}
	
	//named point externalization example
	@After("spring.aspect.ExternalizeAspectExpression.mailServiceMethods()") 
	public void namedPointCutExternalizationExample(JoinPoint jp) {
		System.out.println(" ***** namePointCut externalization example ***** " + jp.getSignature() + " is called");
	}
				
	@Pointcut("execution(* spring.bean.UserManager.*(*))")
	public void userManagerMethods() {}

	@Pointcut("execution(* spring.bean.RegisterService.*(*))")
	public void registerServiceMethods() {}
	
}
