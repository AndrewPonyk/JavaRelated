package ui;

import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Connection;
import java.sql.DriverManager;

public class DatabaseConnectionTesting {

	public static void main(String[] args) {
		try {
			Class.forName("org.apache.derby.jdbc.ClientDriver");
			Connection conn = DriverManager.getConnection("jdbc:derby://localhost/library");
			Statement stm = conn.createStatement();
			ResultSet rs = stm.executeQuery("Select * from materials");
			
			while (rs.next()) {
				System.out.println(rs.getString("title"));
			}
			
			rs.close();
			stm.close();
			conn.close();
		}
		
		catch (Exception e) {
			System.out.println("Something went wrong");
			System.out.println(e);
		}
	}
}
