package com.ap.buildui.client;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.DivElement;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.SpanElement;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;

public class HelloWorldBinder {
	interface MyUiBinder extends UiBinder<DivElement, HelloWorldBinder> {
	}

	private static MyUiBinder uiBinder = GWT.create(MyUiBinder.class);

	@UiField
	SpanElement nameSpan;

	@UiField
	SpanElement secondSpan;

	private DivElement root;

	public HelloWorldBinder() {
		root = uiBinder.createAndBindUi(this);
	}

	public Element getElement() {
		return root;
	}

	public void setName(String name) {
		nameSpan.setInnerText(name);
		secondSpan.setInnerHTML("Second " + name);
	}
}