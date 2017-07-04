package com.ap.todo;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by andrii on 27.06.17.
 */
public class TodoService {
    private static List<Todo> todos = new ArrayList<Todo>();
    static {
        todos.add(new Todo("Learn Web Application Development", "Study"));
        todos.add(new Todo("Learn Spring MVC", "Study"));
        todos.add(new Todo("Learn Spring Rest Services", "Study"));
    }

    public List<Todo> retrieveTodos() {
        return todos;
    }

    public void addTodo(Todo todo) {
        todos.add(todo);
    }

    public void deleteTodo(Todo todo) {
        todos.remove(todo);
    }
}
