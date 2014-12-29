<%@ page isELIgnored="false" contentType="text/html; charset=UTF-8"
	language="java" autoFlush="true"%>
<html>
<body>
	<h2>Hello World !!!</h2>
	Привіт
	
	
	
	<%!private final int five = 0;
	protected String cowboy = "rodeo";

	//The assignment below is not declarative and is a syntax error if uncommented
	//cowboy = "test";
	public long addFive(long number) {
		return number + 5L;
	}

	public class MyInnerClass {
	}

	MyInnerClass instanceVariable = new MyInnerClass();

	//WeirdClassWithinMethod is in method scope, so the declaration below is
	// a syntax error if uncommented
	//WeirdClassWithinMethod bad = new WeirdClassWithinMethod();%>
	<%
		class WeirdClassWithinMethod {
		}
		WeirdClassWithinMethod weirdClass = new WeirdClassWithinMethod();
		MyInnerClass innerClass = new MyInnerClass();
		int seven;
		seven = 7;
	%>
	<%="Hello, World"%><br />
	<%=addFive(12L)%>



	<div style="border: 1px solid black">

		<%
			String[] fruits = request.getParameterValues("fruit");
		%>

		<%
			if (fruits == null) {
		%>You did not select any fruits.<%
			} else {
		%><ul> 
			<%
				for (String fruit : fruits) {
						out.println("<li>" + fruit + "</li>");
					}
			%>
		</ul>
		<%
}
%>

		<form action="index.jsp" method="POST">
			Select the fruits you like to eat:<br /> <input type="checkbox"
				name="fruit" value="Banana" /> Banana<br /> <input type="checkbox"
				name="fruit" value="Apple" /> Apple<br /> <input type="checkbox"
				name="fruit" value="Orange" /> Orange<br /> <input type="checkbox"
				name="fruit" value="Guava" /> Guava<br /> <input type="checkbox"
				name="fruit" value="Kiwi" /> Kiwi<br /> <input type="submit"
				value="Submit" />
		</form>
	</div>
</body>
</html>
