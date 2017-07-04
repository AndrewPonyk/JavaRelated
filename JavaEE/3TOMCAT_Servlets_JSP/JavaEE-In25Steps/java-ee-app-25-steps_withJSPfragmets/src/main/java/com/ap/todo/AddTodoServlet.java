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
@WebServlet(urlPatterns = "/add-todo.do")
public class AddTodoServlet extends HttpServlet {
    private TodoService todoService = new TodoService();

    protected void doGet(HttpServletRequest request,
                         HttpServletResponse response) throws ServletException, IOException {
        request.getRequestDispatcher("/WEB-INF/views/add-todo.jsp").forward(
                request, response);
    }

    protected void doPost(HttpServletRequest request,
                          HttpServletResponse response) throws ServletException, IOException {
        String newTodo = request.getParameter("todo");
        String category = request.getParameter("category");
        todoService.addTodo(new Todo(newTodo, category));
        response.sendRedirect("list-todos.do");
    }
}
