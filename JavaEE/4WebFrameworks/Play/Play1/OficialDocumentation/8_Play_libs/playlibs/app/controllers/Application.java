package controllers;

import play.*;
import play.libs.WS;
import play.libs.WS.HttpResponse;
import play.libs.XPath;
import play.mvc.*;

import java.io.File;
import java.io.IOException;
import java.util.*;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import models.*;

public class Application extends Controller {

	
	
	public static void readXMLWithXPath() throws SAXException, IOException, ParserConfigurationException{
		
	File fXmlFile = new File(Play.applicationPath.toString()+"/"+"doc.xml");
	DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
	DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
	Document doc = dBuilder.parse(fXmlFile);
		
	for(Node event: XPath.selectNodes("events//event", doc)) {
		  String id = XPath.selectText("@id", event);
		
		  System.out.println(id);
	}

		renderArgs.put("ApplicationPath", Play.applicationPath.toString());
		render();
	}
	
	
	
	public static void WebServiceClient(){
		
		HttpResponse res = WS.url("http://www.google.com").get();
		
		
		renderArgs.put("googleContent", res.getString());
		renderArgs.put("ApplicationPath", Play.applicationPath.toString());
		render();
	}
	
	
	
    public static void index() {
    	
    	renderArgs.put("ApplicationPath", Play.applicationPath.toString());
    	render();
    }
}