package com.my;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class ConnectToOracle {
	public static void main(String[] args) throws ClassNotFoundException, SQLException {
		System.out.println("Connecting to oracle db");
		
		Class.forName("oracle.jdbc.driver.OracleDriver");
		Connection conn = DriverManager.getConnection("jdbc:oracle:thin:@localhost:1521:orcl",
				"system", "Aa123456");
		
		Statement statement = conn.createStatement();
		ResultSet rs = statement.executeQuery("select * from users");
		
		while(rs.next()){
			System.out.println(String.format("%s _ %s", rs.getString("ID"), rs.getString("NAME")));
		}
		
		conn.close();
		
	}
}