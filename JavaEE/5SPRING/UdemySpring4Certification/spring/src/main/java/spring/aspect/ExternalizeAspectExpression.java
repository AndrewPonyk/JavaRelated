package spring.aspect;

import org.aspectj.lang.annotation.Pointcut;


public class ExternalizeAspectExpression {

	@Pointcut("execution(* spring.bean.MailService.*(*))")
	public void mailServiceMethods(){}
	
}
