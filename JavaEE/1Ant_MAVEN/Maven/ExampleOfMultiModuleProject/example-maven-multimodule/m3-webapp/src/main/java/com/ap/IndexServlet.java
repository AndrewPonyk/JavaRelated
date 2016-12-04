package com.ap;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

@WebServlet("/index")
public class IndexServlet extends HttpServlet{
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        UtilClassFromM1Module utilClassFromM1Module = new UtilClassFromM1Module();
        PrintWriter writer = resp.getWriter();

        writer.write("Using class from another maven module \n");
        writer.write("Date is " + UtilClassFromM1Module.getNow());
        writer.close();
    }
}
