package com.ap;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

public class PopulateEditKrdmData {
    public static void main(String[] args) throws SQLException, ClassNotFoundException {
        System.out.println("populate edit");

    }

    public static Connection getConnection() throws ClassNotFoundException, SQLException {
        Class.forName("oracle.jdbc.driver.OracleDriver");
        Connection connection = null;
        connection = DriverManager.getConnection("jdbc:oracle:thin:@localhost:49161:xe","system","oracle");
        //connection.close();
        return connection;
    }

    static List<String> entityNames;
    static List<String> entityKeys;


}
