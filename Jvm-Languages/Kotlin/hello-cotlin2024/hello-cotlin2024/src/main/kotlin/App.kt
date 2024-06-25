import com.ap.*;
import java.sql.SQLException;

fun main(args: Array<String>) {
	println("What is your name?");
	//Dog.printCurrentDateTime();

	var s : String;
	s= "test";
	println(s);
	var rex = Dog.create();
	println(rex.javaClass);
	rex.eat();
}