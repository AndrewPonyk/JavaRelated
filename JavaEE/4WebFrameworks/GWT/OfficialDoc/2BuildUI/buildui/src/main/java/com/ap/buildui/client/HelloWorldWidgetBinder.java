package com.ap.buildui.client;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.ListBox;

/**
 * Created by andrew on 10.05.15.
 */
public class HelloWorldWidgetBinder extends Composite {
    interface HelloWorldWidgetBinderUiBinder extends UiBinder<HTMLPanel, HelloWorldWidgetBinder> {
    }

    private static HelloWorldWidgetBinderUiBinder ourUiBinder = GWT.create(HelloWorldWidgetBinderUiBinder.class);

    public HelloWorldWidgetBinder() {
        initWidget(ourUiBinder.createAndBindUi(this));

        listBox.addItem("asb");
        listBox.addItem("abs");
        submitButton.addClickHandler(new ClickHandler() {

			@Override
			public void onClick(ClickEvent event) {
				Window.alert("You select option : " + listBox.getSelectedItemText());
			}
		});
    }

    @UiField
    public Button submitButton;

    @UiField
    public ListBox listBox;
}