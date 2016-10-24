package spring.bean;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class RegisterService {

	// @Autowired and @Resource provide dependency injection to private fields
	@Autowired
	@Qualifier("DBLogService")
	private Log log;
	
	public boolean registerUser(User user) {
		System.out.println("RegisterService registerUser(User u) is called");
		return true;
	}

	public Log getLog() {
		return log;
	}

	public void setLog(Log log) {
		this.log = log;
	}

	
	
}
