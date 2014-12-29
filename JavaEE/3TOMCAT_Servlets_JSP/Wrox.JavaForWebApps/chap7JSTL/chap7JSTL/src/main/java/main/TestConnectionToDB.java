package main;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import oracle.jdbc.driver.OracleDriver;

public class TestConnectionToDB {
	public static void main(String[] args) {
		System.out.println("Testing Oracle DB");
		Connection conn = null;

		try {
			conn = DriverManager.getConnection(
					"jdbc:oracle:thin:@localhost:1521:andrewdb", "system", "Aa123456");

			Statement createStatement = conn.createStatement();
			ResultSet resultSet = createStatement
					.executeQuery("select * from AP_USERS");

			while (resultSet.next()) {
				System.out.println(resultSet.getString(1));
			}

		} catch (SQLException e) {
			e.printStackTrace();
		} finally {
			try {
				conn.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
	}
}