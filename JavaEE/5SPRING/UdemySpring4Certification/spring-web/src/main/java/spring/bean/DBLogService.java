package spring.bean;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.annotation.Transactional;


public class DBLogService implements Log{

	private JdbcTemplate jdbcTemplate;
	
	public DBLogService(DatabaseService databaseService) {
		this.jdbcTemplate = databaseService.getJdbcTemplate();
	}
	
	//JdbcTemplate INSERT example 
	@Transactional(timeout=60)
	public boolean log(final String log) {
		System.out.println("DBLogService : "  + log);		
		final String INSERT_SQL = "INSERT INTO LOG (LOGSTRING) VALUES (?)";
    	try {
    		jdbcTemplate.update(new PreparedStatementCreator() {
				public PreparedStatement createPreparedStatement(Connection connection) throws SQLException {
					PreparedStatement ps = connection.prepareStatement(INSERT_SQL);
					ps.setString(1, log);
					return ps;
				}
			});	
		} catch (Exception e) {
			e.printStackTrace();
		}			
		return false;
	}

	//JdbcTemplate query with in method RowMapper example 
	public List<DBLog> queryAllDBLogs() {
		System.out.println("DBLogService : queryAllDBLogs() is called");	
		final String QUERY_SQL = "SELECT * FROM LOG";
		List<DBLog> dbLogs = this.jdbcTemplate.query(QUERY_SQL, new RowMapper<DBLog>() {
            public DBLog mapRow(ResultSet rs, int rowNum) throws SQLException {
            	DBLog dbLog = new DBLog();
            	dbLog.setIDLOG(rs.getInt("IDLOG"));
            	dbLog.setLOGSTRING(rs.getString("LOGSTRING"));
                return dbLog;
            }
        });
        return dbLogs; 
	}
}
