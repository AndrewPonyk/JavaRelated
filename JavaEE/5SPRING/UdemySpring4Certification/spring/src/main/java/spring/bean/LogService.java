package spring.bean;

import org.springframework.stereotype.Component;

@Component
public class LogService implements Log{

	public boolean log (String log) {
		System.out.println("LogService : "  + log);
		return true;
	}

}

