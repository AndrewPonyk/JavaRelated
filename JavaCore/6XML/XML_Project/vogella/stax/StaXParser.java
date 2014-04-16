package stax;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.xml.stream.XMLEventFactory;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLEventWriter;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.Characters;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.StartDocument;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

import com.sun.xml.internal.stream.writers.XMLWriter;

public class StaXParser {

	static final String DATE = "date";
	static final String ITEM = "item";
	static final String MODE = "mode";
	static final String UNIT = "unit";
	static final String CURRENT = "current";
	static final String INTERACTIVE = "interactive";

	static final String FILENAME = System.getProperty("user.dir")
			+ "/xml_files/items.xml";

	static final String OUT_FILENAME = System.getProperty("user.dir")
			+ "/xml_files/out_items.xml";

	
	public static void main(String[] args) {
		System.out.println("Parse xml using StaX  (using events api)");

		List<Item> items = readConfig(FILENAME);

		for (Item item : items) {
			System.out.println(item);
		}
		
		
		writeConfig(OUT_FILENAME, null);
	}

	public static List<Item> readConfig(String configFile) {
		List<Item> items = new ArrayList<Item>();

		try {
			// First, create a new XMLInputFactory
			XMLInputFactory inputFactory = XMLInputFactory.newInstance();

			InputStream in = new FileInputStream(configFile);
			XMLEventReader eventReader = inputFactory.createXMLEventReader(in);

			Item item = null;

			while (eventReader.hasNext()) {
				XMLEvent event = eventReader.nextEvent();

				if (event.isStartElement()) {
					StartElement startElement = event.asStartElement();
					// If we have an item element, we create a new item
					if (startElement.getName().getLocalPart() == (ITEM)) {
						item = new Item();
						// We read the attributes from this tag and add the date
						// attribute to our object
						Iterator<Attribute> attributes = startElement
								.getAttributes();
						while (attributes.hasNext()) { // reading attributes
							Attribute attribute = attributes.next();
							if (attribute.getName().toString().equals(DATE)) {
								item.date = attribute.getValue();
							}

						}
					}
				}
				// ///////////////////////

				if (event.isStartElement()) {

					if (event.asStartElement().getName().getLocalPart()
							.equals(MODE)) {
						event = eventReader.nextEvent();
						item.mode = event.asCharacters().getData();
						continue;
					}

					// ////////////////////

					if (event.asStartElement().getName().getLocalPart()
							.equals(UNIT)) {
						event = eventReader.nextEvent();
						item.unit = event.asCharacters().getData();
						continue;
					}

					// ////////////////////////////
					if (event.asStartElement().getName().getLocalPart()
							.equals(CURRENT)) {
						event = eventReader.nextEvent();
						item.current = event.asCharacters().getData();
						continue;
					}
					// ///////////////////////////
					if (event.asStartElement().getName().getLocalPart()
							.equals(INTERACTIVE)) {
						event = eventReader.nextEvent();
						item.interactive = event.asCharacters().getData();
						continue;
					}
				}
				// ////////////////////////////

				if (event.isEndElement()) {
					EndElement endElement = event.asEndElement();
					if (endElement.getName().getLocalPart() == (ITEM)) {
						items.add(item);
					}
				}
			}

		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (XMLStreamException e) {
			e.printStackTrace();
		}

		return items;
	}

	public static void writeConfig(String configFile, List<Item> items) {

		try {
			// create an XMLOutputFactory
			XMLOutputFactory outputFactory = XMLOutputFactory.newInstance();

			XMLEventWriter eventWriter = outputFactory
					.createXMLEventWriter(new FileOutputStream(FILENAME, true));

			// create an EventFactory
			XMLEventFactory eventFactory = XMLEventFactory.newInstance();
			XMLEvent end = eventFactory.createDTD("\n");

			// create and write Start Tag
			StartDocument startDocument = eventFactory.createStartDocument();

			eventWriter.add(startDocument);

			
			// create config open tag
			StartElement configStartElement = eventFactory.createStartElement(
					"", "", "config");

			eventWriter.add(configStartElement);
			eventWriter.add(end);

			// Write the different nodes
			createNode(eventWriter, "mode", "1");
			createNode(eventWriter, "unit", "901");
			createNode(eventWriter, "current", "0");
			createNode(eventWriter, "interactive", "0");

			eventWriter.add(eventFactory.createEndElement("", "", "config"));

			eventWriter.add(end);
			eventWriter.add(eventFactory.createEndDocument());
			eventWriter.close();

		} catch (FileNotFoundException | XMLStreamException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return;
	}

	private static void createNode(XMLEventWriter eventWriter, String name,
		String value) throws XMLStreamException {
		XMLEventFactory eventFactory = XMLEventFactory.newInstance();
		XMLEvent end = eventFactory.createDTD("\n");
		XMLEvent tab = eventFactory.createDTD("\t");

		// create Start node
		StartElement sElement = eventFactory.createStartElement("", "", name);

		eventWriter.add(tab);
		eventWriter.add(sElement);

		// create Content
		Characters characters = eventFactory.createCharacters(value);
		eventWriter.add(characters);

		// create End node
		EndElement eElement = eventFactory.createEndElement("", "", name);
		eventWriter.add(eElement);
		eventWriter.add(end);

	}
}

class Item {
	public String date;
	public String mode;
	public String unit;
	public String current;
	public String interactive;

	@Override
	public String toString() {
		return "Item [date=" + date + ", mode=" + mode + ", unit=" + unit
				+ ", current=" + current + ", interactive=" + interactive + "]";
	}
}
