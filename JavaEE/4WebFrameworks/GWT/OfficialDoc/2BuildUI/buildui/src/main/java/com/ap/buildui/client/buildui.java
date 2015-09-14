package com.ap.buildui.client;

import com.ap.buildui.client.CellDataGrid.CwDataGrid;
import com.ap.buildui.client.uibiners.HelloWorldBinder;
import com.ap.buildui.client.uibiners.HelloWorldWidgetBinder;
import com.google.gwt.cell.client.TextCell;
import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.event.dom.client.*;
import com.google.gwt.user.cellview.client.CellList;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Entry point classes define <code>onModuleLoad()</code>.
 */
public class buildui implements EntryPoint {

    private OptionalTextBox optionalTextBox;

    private StudyDOM studyDOM = new StudyDOM();

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

        //
        learnHorizontalPanel();

        //
        learnSplitLayoutPanel();

        //
        learnDockLayoutPanel();

        //
        learnTreeWidget();

        //
        learnStackPanel();

        //
        learnCustomWidget();

        //////////////////
        learnCELLWidgets();

        //
        learnDataGridBasedOnCells();

        //
        learnGwtEditors();

        //
        studyDOM();

    }

    private void studyDOM() {
        // Adding link to page
        Anchor google = new Anchor("Link to GOOGLe", "http://google.com");
        setIdToWidget(google, "googleLink");
        RootPanel.get().add(google);


        ArrayList<String> idList = new ArrayList<String>();
        studyDOM.putElementLinkIDsInList(RootPanel.getBodyElement(), idList);
        Window.alert("Count of links on page: " + idList.size());

        studyDOM.rewriteLinksIterative();

        // interesting things in this method
        studyDOM.captureBrowserEvent(google.getElement());

        google.addClickHandler(new ClickHandler() {
            @Override
            public void onClick(ClickEvent clickEvent) {
                Window.alert("Standard click handler");
            }
        });

    }

    private void learnGwtEditors() {
    }

    private void learnUIDivBinder() {
        HelloWorldBinder binder = new HelloWorldBinder();
        binder.setName("WORLD");

        Document.get().getBody().appendChild(binder.getElement());

    }

    private void learnUIWidgetBinder() {
        HelloWorldWidgetBinder widgetBinder = new HelloWorldWidgetBinder();
        RootPanel.get().add(widgetBinder);

    }

    //---------------------- Learn Different Panels
    private void learnFlowPanel() {
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

    private void learnHorizontalPanel() {
        HorizontalPanel hp = new HorizontalPanel();

        hp.getElement().setAttribute("style", "margin:10px; border: 2px solid red");
        hp.getElement().setAttribute("id", "");
        hp.add(new Label("This is horizontal panel example"));
        hp.add(new Button("123"));
        hp.add(new Button("456"));

        RootPanel.get().add(hp);
    }


    private void learnDockLayoutPanel() {
        DockLayoutPanel sp = new DockLayoutPanel(Unit.PX);
        sp.ensureDebugId("cwDockLayoutPanel");
        sp.getElement().getStyle()
                .setProperty("border", "3px solid blue");
        sp.getElement().getStyle()
                .setProperty("margin", "20px 0");

        // Add text all around.
        sp.addNorth(new Label("North element .... DockLayoutPanel"), 50);
        sp.addSouth(new Label("South elemen ..a"), 50);
        sp.addEast(new Label("East east text"), 100);
        sp.addWest(new Label("It is west stext"), 100);
        sp.addNorth(new Label("This is north2 text"), 50);
        sp.addSouth(new Label("This is south2 text"), 50);
        sp.add(new Label("This is siple CENTER CENTER CENTER text "));
        sp.setSize("1000px", "300px");

        RootPanel.get().add(sp);
    }

    /*The SplitLayoutPanel is identical to the DockLayoutPanel (and indeed extends it), except
    that it automatically creates a user-draggable splitter between each pair of child widgets.
    It also supports only the use of pixel units.
    Use this instead of HorizontalSplitPanel and VerticalSplitPanel.
    */
    private void learnSplitLayoutPanel() {

        SplitLayoutPanel sp = new SplitLayoutPanel(5);
        sp.ensureDebugId("cwSplitLayoutPanel");
        sp.getElement().getStyle()
                .setProperty("border", "3px solid blue");

        // Add text all around.
        sp.addNorth(new Label("North element ..."), 50);
        sp.addSouth(new Label("South elemen ..a"), 50);
        sp.addEast(new Label("East east text"), 100);
        sp.addWest(new Label("It is west stext"), 100);
        sp.addNorth(new Label("This is north2 text"), 50);
        sp.addSouth(new Label("This is south2 text"), 50);
        sp.add(new Label("This is siple CENTER CENTER CENTER text "));
        sp.setSize("1000px", "300px");

        RootPanel.get().add(sp);
    }

    private void learnStackPanel() {
        // Create a three-item stack, with headers sized in EMs.
        StackPanel p = new StackPanel();
        p.add(new HTML("this "), "[this title]");
        p.add(new HTML("that "), "[that title]");
        p.add(new HTML("the other "), "[the other title]");

        FlowPanel fp = new FlowPanel();

        // Attach the LayoutPanel to the RootLayoutPanel. The latter will listen for
        // resize events on the window to ensure that its children are informed of
        // possible size changes.
        fp.add(p);
        RootPanel.get().add(fp);
    }

    private void learnPopupPanelAndDialogBox() {
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

                simplePopup.setPopupPosition(event.getClientX() + 10, event.getClientY() + 10);
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

    private void learnTabLayoutPanel() {
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

    private void learnTreeWidget() {
        // Create a tree with a few items in it.
        TreeItem root = new TreeItem(new HTML("root"));
        root.addItem(new HTML("item0"));
        root.addItem(new HTML("item1"));
        root.addItem(new HTML("item2"));

        // Add a CheckBox to the tree
        TreeItem item = new TreeItem(new CheckBox("item3"));
        root.addItem(item);

        Tree t = new Tree();
        t.addItem(root);

        // Add it to the root panel.
        RootPanel.get().add(t);
    }

    private void learnCustomWidget() {
        RootPanel.get().add(new HTML("<hr>"));
        optionalTextBox = new OptionalTextBox("This is custom widget: optional textbox");
        RootPanel.get().add(optionalTextBox);
        RootPanel.get().add(new HTML("<hr>"));
    }

    private void learnCELLWidgets() {
        final List<String> DAYS = Arrays.asList("Sunday", "Monday",
                "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday");

        // Create a cell to render each value in the list.
        TextCell textCell = new TextCell();
        // Create a CellList that uses the cell.
        CellList<String> cellList = new CellList<String>(textCell);

        // Set the total row count. This isn't strictly necessary, but it affects
        // paging calculations, so its good habit to keep the row count up to date.
        cellList.setRowCount(DAYS.size(), true);

        // Push the data into the widget.
        cellList.setRowData(0, DAYS);

        // Add it to the root panel.
        RootPanel.get().add(cellList);
    }

    private void learnDataGridBasedOnCells() {
        CwDataGrid dataGrid = new CwDataGrid();
        dataGrid.setSize("900px", "500px");
        RootPanel.get().add(dataGrid);
    }

    private void setIdToWidget (Widget widget, String id){
        widget.getElement().setAttribute("id", id);
    }


    public void cssAndGwt(){
        /*There are multiple approaches for associating CSS files with your module:

        - Using a <link> tag in the host HTML page.
        - Using the <stylesheet> element in the module XML file.
        - Using a CssResource contained within a ClientBundle.
        - Using an inline <ui:style> element in a UiBinder template.
        */
    }

}