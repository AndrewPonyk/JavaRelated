package com.ap.buildui.client.uibiners;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.*;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.Window;

public class HelloWorldBinder {
	interface MyUiBinder extends UiBinder<DivElement, HelloWorldBinder> {
	}

	private static MyUiBinder uiBinder = GWT.create(MyUiBinder.class);

	@UiField
	SpanElement nameSpan;

	@UiField
	SpanElement secondSpan;

	@UiField
	UListElement results;

	private DivElement root;

	public HelloWorldBinder() {
		root = uiBinder.createAndBindUi(this);
	}

	public Element getElement() {
		return root;
	}

	public void setName(String name) {
		Window.alert("123");
		nameSpan.setInnerText(name);
		secondSpan.setInnerHTML("Second " + name);
		for(int i = 0 ;i< 3 ;i++){
			LIElement result = Document.get().createLIElement();
			result.setInnerHTML("" + i + i);
			results.appendChild(result);
		}
	}
}