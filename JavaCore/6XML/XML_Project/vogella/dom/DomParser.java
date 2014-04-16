package dom;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Attr;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class DomParser {
	final static String FILENAME = System.getProperty("user.dir")
			+ "/xml_files/dom_Document.xml";

	final static String OUT_FILENAME = System.getProperty("user.dir") 
			+ "/xml_files/out_dom_Document.xml";
	
	public static void main(String[] args) {

		List<Employee> employees = readXml();
		// display elements from XML
		for (Employee item : employees) {
			System.out.println(item);
		}
		
		writeToXMLUsingDom(employees, OUT_FILENAME);
		
	}

	private static List<Employee> readXml() {
		// Get the DOM Builder Factory
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();

		// Get the DOM Builder
		try {
			DocumentBuilder builder = factory.newDocumentBuilder();

			Document document = builder.parse(new FileInputStream(FILENAME));

			List<Employee> employees = new ArrayList<>();

			NodeList nodeList = document.getDocumentElement().getChildNodes();
			for (int i = 0; i < nodeList.getLength(); i++) {
				// We have encountered an <employee> tag.
				Node node = nodeList.item(i);

				if (node instanceof Element) {

					Employee emp = new Employee();

					emp.id = node.getAttributes().getNamedItem("id")
							.getNodeValue();
					NodeList childNodes = node.getChildNodes();

					for (int j = 0; j < childNodes.getLength(); j++) {
						Node cNode = childNodes.item(j);

						// Identifying the child tag of employee encountered.
						if (cNode instanceof Element) {
							String content = cNode.getLastChild()
									.getTextContent().trim();
							switch (cNode.getNodeName()) {
							case "firstName":
								emp.firstName = content;
								break;
							case "lastName":
								emp.lastName = content;
								break;
							case "location":
								emp.location = content;
								break;
							}

						}
					}
					employees.add(emp);

				}
			}

			return employees;

		} catch (ParserConfigurationException e) {
			e.printStackTrace();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (SAXException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}

	public static void writeToXMLUsingDom(List<Employee> emp, String filename) {
		try {
			DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
			
			Document doc = builder.newDocument();
			
			Element rootElement = doc.createElement("employees");
			doc.appendChild(rootElement);
			
			for(Employee item : emp){
				Element employee = doc.createElement("employee");
				rootElement.appendChild(employee);
				
				Attr id = doc.createAttribute("id");
				id.setValue(item.id);
				employee.setAttributeNode(id);
				
				Element firstname = doc.createElement("firstname");
				firstname.appendChild(doc.createTextNode(item.firstName));
				employee.appendChild(firstname);
				
				Element lastname = doc.createElement("lastname");
				firstname.appendChild(doc.createTextNode(item.lastName));
				employee.appendChild(lastname);
				
				Element location = doc.createElement("location");
				firstname.appendChild(doc.createTextNode(item.location));
				employee.appendChild(location);
				
			}
			
			Transformer transformer = TransformerFactory.newInstance().newTransformer();
			DOMSource source = new DOMSource(doc);
			StreamResult result = new StreamResult(new FileOutputStream(filename));
			
			transformer.transform(source, result);
			
		} catch (ParserConfigurationException e) {
			e.printStackTrace();
		} catch (TransformerConfigurationException e) {
			e.printStackTrace();
		} catch (TransformerFactoryConfigurationError e) {
			e.printStackTrace();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (TransformerException e) {
			e.printStackTrace();
		}
		
		return;
	}

}

class Employee {
	String id;
	String firstName;
	String lastName;
	String location;

	@Override
	public String toString() {
		return firstName + " " + lastName + "(" + id + ")" + location;
	}
}