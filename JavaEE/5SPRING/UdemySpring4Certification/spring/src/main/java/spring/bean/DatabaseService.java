package spring.bean;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;


@Repository
public class DatabaseService {
	
	@Autowired
	public DatabaseService(DataSource dataSource) {		
		System.out.println("Datasource through Constructor injection in DatabaseService");	
		System.out.println("DataSource: " + dataSource.getDriverClassName());
		System.out.println("DataSource: " + dataSource.getUrl());
	}
		
	
	//javax.annotation approach
	@PostConstruct
	public void init() {
	      //initialization work
		  System.out.println("@PostConstruct :" + "is called");
	}
	
	//javax.annotation approach
	@PreDestroy
	public void destroy() {
	      //destruction work
		  System.out.println("@PreDestroy :" + "is called");
	}	
	
}
