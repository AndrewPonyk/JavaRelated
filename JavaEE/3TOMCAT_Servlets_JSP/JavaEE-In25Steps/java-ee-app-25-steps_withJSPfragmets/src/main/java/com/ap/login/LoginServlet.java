package com.ap.login;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@WebServlet("/login.do")
public class LoginServlet extends HttpServlet{
    private LoginService userValidationService = new LoginService();

    protected void doGet(HttpServletRequest request,
                         HttpServletResponse response) throws ServletException, IOException {
        request.getRequestDispatcher("/WEB-INF/views/login.jsp").forward(
                request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request,
                          HttpServletResponse response) throws ServletException, IOException {
        String name = request.getParameter("name");
        String password = request.getParameter("password");

        boolean isUserValid = userValidationService.isUserValid(name, password);

        if (isUserValid) {
            request.getSession().setAttribute("name", name);
            response.sendRedirect("list-todos.do");
        } else {
            request.setAttribute("errorMessage", "Invalid Credentials!");
            request.getRequestDispatcher("/WEB-INF/views/login.jsp").forward(
                    request, response);
        }
    }
}
