package com.ap.codingbasics.client.overlaytypes;

import com.google.gwt.core.client.JsArray;
import com.google.gwt.core.client.JsonUtils;
import com.google.gwt.user.client.Window;

public class OverlayExamples {

	public OverlayExamples(){
	}

	public void parsingJsonStringToOrerlayArray(){
		String jsonData = "["
		       		+	"{ \"FirstName\" : \"Jimmy\", \"LastName\" : \"Webber\" },"
		    		+	"{ \"FirstName\" : \"Alan\",  \"LastName\" : \"Dayal\" },"
		    		+	"{ \"FirstName\" : \"Keanu\", \"LastName\" : \"Spoon\" },"
		    		+	"{ \"FirstName\" : \"Emily\", \"LastName\" : \"Rudnick\" }"
		    		+	"]";

		JsArray<Customer> customers = JsonUtils.<JsArray<Customer>>safeEval(jsonData);

		String resultFirstNames = "First Names : ";
		for(int i = 0; i < customers.length(); i++) {
			resultFirstNames += customers.get(i).getFirstName() + " ";
		}
		Window.alert(resultFirstNames);
	}

	public void parsingJSObject(){
		Customer firstCustomer = getFirstCustomer();
		Window.alert("First customer is : " + firstCustomer.getFullName());
	}

	public native Customer getFirstCustomer()/*-{
		var jsonData = [
			{ "FirstName" : "Jimmy", "LastName" : "Webber" },
			{ "FirstName" : "Alan",  "LastName" : "Dayal" },
			{ "FirstName" : "Keanu", "LastName" : "Spoon" },
			{ "FirstName" : "Emily", "LastName" : "Rudnick" }
		];
		return jsonData[0];
	}-*/;
}
