package sax;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

public class SaxParser {
	final static String FILENAME = System.getProperty("user.dir") + "/xml_files/dom_Document.xml";
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		try {
			SAXParser parser = SAXParserFactory.newInstance().newSAXParser();
			SAXHandler handler = new SAXHandler();
			parser.parse(new File(FILENAME), handler);
			
			
			for(Employee item : handler.empList){
				System.out.println(item);
			}
			
		} catch (ParserConfigurationException e) {
			e.printStackTrace();
		} catch (SAXException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}
		
	}
}

class SAXHandler extends DefaultHandler{
	
	String content = null;
	Employee emp = null;
	List<Employee> empList = new ArrayList<>();
	
	@Override
	public void startElement(String uri, String localName, String qName,
			Attributes attributes) throws SAXException {
		switch (qName) {
		case "employee":
			emp = new Employee();
			emp.id = attributes.getValue("id");
			break;
		}
	}
	
	@Override
	public void endElement(String uri, String localName, String qName)
			throws SAXException {
		switch (qName) {
		case "firstName":
			emp.firstName = content;
			break;
		case "lastName":
			emp.lastName = content;
			break;
		case "location":
			emp.location = content;
			break;
		case "employee":
			empList.add(emp);
			break;
		}
	}
	
	@Override
	public void characters(char[] ch, int start, int length)
			throws SAXException {
		content = String.copyValueOf(ch, start, length);
	}
	
}

class Employee {
	public String id;
	public String firstName;
	public String lastName;
	public String location;

	@Override
	public String toString() {
		return firstName + " " + lastName + "(" + id + ")" + location;
	}
}