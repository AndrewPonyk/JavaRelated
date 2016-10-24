package spring.bean;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
@Qualifier("DBLogService")
public class DBLogService implements Log{

	public boolean log(String log) {
		System.out.println("DBLogService : "  + log);
		return false;
	}

}
