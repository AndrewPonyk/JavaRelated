package com.ap.todo;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Created by andrii on 27.06.17.
 */
@WebServlet(urlPatterns = "/list-todos.do")
public class ListTodoServlet extends HttpServlet {
    private TodoService todoService = new TodoService();

    protected void doGet(HttpServletRequest request,
                         HttpServletResponse response) throws ServletException, IOException {
        request.setAttribute("todos", todoService.retrieveTodos());
        request.getRequestDispatcher("/WEB-INF/views/list-todos.jsp").forward(
                request, response);
    }
}
