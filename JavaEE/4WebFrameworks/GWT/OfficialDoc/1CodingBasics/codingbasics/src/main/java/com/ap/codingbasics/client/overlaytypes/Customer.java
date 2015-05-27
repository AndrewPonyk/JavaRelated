package com.ap.codingbasics.client.overlaytypes;

import com.google.gwt.core.client.JavaScriptObject;

public class Customer extends JavaScriptObject {

	protected Customer(){

	}

	// Typically, methods on overlay types are JSNI
	public final native String getFirstName() /*-{
		return this.FirstName;
	}-*/;

	public final native String getLastName() /*-{
		return this.LastName;
	}-*/;

	// Note, though, that methods aren't required to be JSNI
	public final String getFullName() {
		return getFirstName() + " " + getLastName();
	}
}
