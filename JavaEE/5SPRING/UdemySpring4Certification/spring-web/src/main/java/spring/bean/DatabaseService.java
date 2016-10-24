package spring.bean;

import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;

public class DatabaseService {
	
	private JdbcTemplate jdbcTemplate;
	
	public DatabaseService(DataSource dataSource) {		
		System.out.println("Database Service: " + dataSource);
		//Setting data source through constructor
		this.setJdbcTemplate(new JdbcTemplate(dataSource));
	}

	public JdbcTemplate getJdbcTemplate() {
		return jdbcTemplate;
	}

	public void setJdbcTemplate(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	
	
}
