
public class Main {

	public static void main(String[] args) {

		Book book1 = new Book(1,"An introduction to Java","Matt Greencroft","12345");
		Book book2 = new Book(2,"Better Java","Joe Le Blanc","23456");

		UI ui = new UI();
		ui.printHeader();
		ui.printBook(book1);
		ui.printBook(book2);
	}
	
}
