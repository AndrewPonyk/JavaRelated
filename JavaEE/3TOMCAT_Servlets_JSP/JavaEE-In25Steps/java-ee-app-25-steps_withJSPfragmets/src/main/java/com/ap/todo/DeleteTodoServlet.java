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
@WebServlet(urlPatterns = "/delete-todo.do")
public class DeleteTodoServlet extends HttpServlet {
    private TodoService todoService = new TodoService();

    protected void doGet(HttpServletRequest request,
                         HttpServletResponse response) throws ServletException, IOException {
        todoService.deleteTodo(new Todo(request.getParameter("todo"), request
                .getParameter("category")));
        response.sendRedirect("list-todos.do");
    }
}
