package com.ap.dao;

import com.ap.connection.ConnectionDB;
import com.ap.form.LoginForm;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class ImplLoginImpl implements Ifacelogin {
    @Override
    public LoginForm validateLogin(LoginForm obj) {

        Connection conn = new ConnectionDB().getConnection();
        LoginForm bean = null;
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("select * from login where usuario = ? and password = ?");
            PreparedStatement ps = conn.prepareStatement(sb.toString());
            ps.setString(1, obj.getUser());
            ps.setString(2, obj.getPassword());
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                bean = new LoginForm();
                bean.setUser(rs.getString("usuario"));
                bean.setPassword(rs.getString("password"));
                bean.setNombre(rs.getString("nombre"));
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return bean;
    }
}