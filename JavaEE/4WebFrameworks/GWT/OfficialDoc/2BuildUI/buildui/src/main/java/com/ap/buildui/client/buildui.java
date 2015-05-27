package com.ap.buildui.client;

import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.event.dom.client.MouseOutEvent;
import com.google.gwt.event.dom.client.MouseOutHandler;
import com.google.gwt.event.dom.client.MouseOverEvent;
import com.google.gwt.event.dom.client.MouseOverHandler;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.*;

/**
 * Entry point classes define <code>onModuleLoad()</code>.
 */
public class buildui implements EntryPoint {

	/**
	 * This is the entry point method.
	 */
	public void onModuleLoad() {

		learnPopupPanelAndDialogBox();
		//
		learnTabLayoutPanel();

		//
		learnFlowPanel();

		//
		learnUIDivBinder();

		//
		learnUIWidgetBinder();
	}


	private void learnUIDivBinder(){
		HelloWorldBinder binder = new HelloWorldBinder();
		binder.setName("WORLD");

		Document.get().getBody().appendChild(binder.getElement());
	}

	private void learnUIWidgetBinder(){
		HelloWorldWidgetBinder widgetBinder = new HelloWorldWidgetBinder();
		RootPanel.get().add(widgetBinder);
	}

	//---------------------- Learn Different Panels
	private void learnFlowPanel(){
	/*A FlowPanel is the simplest panel. It creates a single <div> element and attaches children directly
	to it without modification.
	Use it in cases where you want the natural HTML flow to determine the layout of child widgets.*/
		FlowPanel flowPanel = new FlowPanel();
		for (int i = 0; i < 30; i++) {
			CheckBox checkbox = new CheckBox("Check " + " " + i);
			checkbox.addStyleName("cw-FlowPanel-checkBox");
			flowPanel.add(checkbox);
			flowPanel.addStyleName("border1px");
			flowPanel.setTitle("Flow panel example");

			RootPanel.get().add(flowPanel);
		}
	}

	private void learnHorizontalPanel(){

	}

	private void learnVerticalSplitPanel(){

	}

	private void learnStackPanel(){

	}

	private void learnPopupPanelAndDialogBox(){
		FlowPanel flowPanel = new FlowPanel();
		flowPanel.add(new Button("0"));
		flowPanel.add(new Button("0"));
		flowPanel.add(new Button("0"));
		flowPanel.add(new HTML("<hr>"));
		flowPanel.getElement().setAttribute("style", "border: 1px solid #ad2");

		final DecoratedPopupPanel simplePopup = new DecoratedPopupPanel(true);
		simplePopup.ensureDebugId("cwBasicPopup-simplePopup");
		simplePopup.setWidth("150px");
		simplePopup.setWidget(new HTML("ThIS IS POPUP ON FLOW PANEL WITH 3 buttons "));

		flowPanel.addDomHandler(new MouseOverHandler() {
			@Override
			public void onMouseOver(MouseOverEvent event) {

				simplePopup.setPopupPosition(event.getClientX()+10, event.getClientY()+10);
				simplePopup.show();
			}
		}, MouseOverEvent.getType());
		flowPanel.addDomHandler(new MouseOutHandler() {

			@Override
			public void onMouseOut(MouseOutEvent event) {
				simplePopup.hide();
			}
		}, MouseOutEvent.getType());


		RootPanel.get().add(flowPanel);
	}

	private void learnTabLayoutPanel(){
		TabLayoutPanel p = new TabLayoutPanel(1.5, Unit.EM);
		p.setHeight("200px");
		p.setWidth("500px");
		p.add(new HTML("this content"), "this");
		p.add(new HTML("that content"), "that");
		p.add(new HTML("the other content"), "the other");
		p.setAnimationDuration(500);
		RootPanel.get().add(p);
	}
	//End---------------------- Learn Different Panels
}