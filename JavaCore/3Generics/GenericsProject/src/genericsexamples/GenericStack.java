package genericsexamples;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Stack;

public class GenericStack<E> {
	private final int size;
	private int top;
	private E[] elements;

	public GenericStack() {
		this(10);
	}

	public GenericStack(int s) {
		size = s > 0 ? s : 10;
		top = -1;
		elements = (E[]) new Object[size];
	}

	public void push(E pushValue) throws Exception {
		if (top == size - 1) // if stack is full
			throw new Exception(String.format("Stack is full, cannot push %s",
					pushValue));
		/*
		 * if (top == size - 1) // if stack is full if (top == elements.length -
		 * 1) resize(2 * elements.length);
		 */
	
		elements[++top] = pushValue; // place pushValue on Stack
	} // end method push

	public E pop() throws Exception {
		if (top == -1) // if stack is empty
			throw new Exception("Stack is empty, cannot pop");

		return elements[top--];
	}

	public Boolean isEmpty() {
		return top == -1;
	}

	private void resize(int newSize) {
		E t[] = (E[]) new Object[newSize];
		for (int i = 0; i <= top; i++)
			t[i] = elements[i];
		elements = t;
	}

	public static void main(String[] args) throws Exception {
		System.out.println("Using stack");

		GenericStack<Integer> stack = new GenericStack<>();

		stack.push(10);
		stack.push(100);
		stack.push(1000);

		while (!stack.isEmpty()) {
			System.out.println(stack.pop());
		}

	}
}
