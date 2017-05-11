package ch21IntroToCollections;

import ch19PracticalSessionLoanRegistry.models.Book;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;


public class ImprovingArrays {


	public static void main(String[] args) {
		ArrayList<String> myArrayList = new ArrayList<String>();
		HashSet<String> myHashSet = new HashSet<String>();

		myArrayList.add("first item");
		boolean result = myArrayList.add("second item");
		System.out.println(result);
		myArrayList.add("third item");
		myArrayList.add("fourth item");
		
		myHashSet.add("first item");
		myHashSet.add("second item");
		myHashSet.add("third item");
		myHashSet.add("fourth item");

		System.out.println(myArrayList.size());
		System.out.println(myHashSet.size());

		myArrayList.remove(1);

		myHashSet.remove("second item");
		System.out.println(myHashSet.size());

		myArrayList.add("first item");
		myArrayList.add(1,"first item");

		myHashSet.add("first item");

		System.out.println(myArrayList.size());
		System.out.println(myHashSet.size());

		for (int i = 0; i < myArrayList.size(); i++) {
			System.out.println(myArrayList.get(i));
		}

		System.out.println("");

		Iterator<String> myIterator = myHashSet.iterator();

		while(myIterator.hasNext()) {
			String nextString = myIterator.next();
			System.out.println(nextString);

		}


		HashMap<String, Book> myHashMap = new HashMap<String, Book>();

		Book book1 = new Book(1,"first book","","","",100);
		Book book2 = new Book(2,"second book","","","",100);
		Book book3 = new Book(3,"third book","","","",100);

		myHashMap.put(book1.getTitle(),book1);
		myHashMap.put(book2.getTitle(),book2);
		myHashMap.put(book3.getTitle(),book3);

		System.out.println(myHashMap.size());

		myHashMap.remove(book2.getTitle());
		System.out.println(myHashMap.size());

		myHashMap.put(book2.getTitle(),book2);

		Iterator<Book> myValues = myHashMap.values().iterator();

		while(myValues.hasNext()) {
			Book nextBook = myValues.next();
			System.out.println(nextBook.getTitle());

		}
	}

}
