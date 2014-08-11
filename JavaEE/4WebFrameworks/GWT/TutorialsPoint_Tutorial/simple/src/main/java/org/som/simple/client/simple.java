package org.som.simple.client;

import org.som.simple.shared.FieldVerifier;

import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.Scheduler.ScheduledCommand;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyUpEvent;
import com.google.gwt.event.dom.client.KeyUpHandler;
import com.google.gwt.event.dom.client.MouseOverEvent;
import com.google.gwt.event.dom.client.MouseOverHandler;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.DialogBox;
import com.google.gwt.user.client.ui.FileUpload;
import com.google.gwt.user.client.ui.FormPanel;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.HasHorizontalAlignment;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.MenuBar;
import com.google.gwt.user.client.ui.MenuItem;
import com.google.gwt.user.client.ui.MultiWordSuggestOracle;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwt.user.client.ui.SuggestBox;
import com.google.gwt.user.client.ui.TabPanel;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.Widget;

/**
 * Entry point classes define <code>onModuleLoad()</code>.
 */
public class simple implements EntryPoint {
	/**
	 * The message displayed to the user when the server cannot be reached or
	 * returns an error.
	 */
	private static final String SERVER_ERROR = "An error occurred while "
			+ "attempting to contact the server. Please check your network "
			+ "connection and try again.";

	/**
	 * Create a remote service proxy to talk to the server-side Greeting
	 * service.
	 */
	private final GreetingServiceAsync greetingService = GWT
			.create(GreetingService.class);

	private final Messages messages = GWT.create(Messages.class);
	public String testMessage = "Some text";

	/**
	 * This is the entry point method.
	 */
	public void onModuleLoad() {
		/********************/
		Window.alert("Alert from java");

		HTML html = new HTML("<h1>Welcome to GWT APP (text from java)</h1>");
		RootPanel.getBodyElement().insertFirst(html.getElement());

		final Button button1 = new Button("Button1");
		button1.addClickHandler(new ClickHandler() {
			public void onClick(ClickEvent arg0) {
				Window.alert("click");
				logToConsole();
			}
		});
		RootPanel.get("heading").add(button1);

		Button redButton = new Button("Red");
		redButton.setStyleName("red-button");
		redButton.setSize("100px", "100px");

		// add a clickListener to the button
		redButton.addClickHandler(new ClickHandler() {

			public void onClick(ClickEvent event) {
				Window.alert("Red Button clicked!");
			}
		});
		RootPanel.get("formWidgets").add(redButton);

		// Suggest text box widget
		// create the suggestion data
		MultiWordSuggestOracle oracle = new MultiWordSuggestOracle();
		oracle.add("A");
		oracle.add("AB");
		oracle.add("ABC");
		oracle.add("ABCD");
		oracle.add("B");
		oracle.add("BC");
		oracle.add("Oracle");

		SuggestBox suggestionBox = new SuggestBox(oracle);
		RootPanel.get("formWidgets").add(suggestionBox);

		// /**** upload file using GWT
		VerticalPanel panel = new VerticalPanel();
		// create a FormPanel
		final FormPanel form = new FormPanel();
		// create a file upload widget
		final FileUpload fileUpload = new FileUpload();

		// create labels
		Label selectLabel = new Label("Select a file:");
		// create upload button
		Button uploadButton = new Button("Upload File");
		// pass action to the form to point to service handling file
		// receiving operation.
		form.setAction("handleUpload.jsp");
		// set form to use the POST method, and multipart MIME encoding.
		form.setEncoding(FormPanel.ENCODING_MULTIPART);
		form.setMethod(FormPanel.METHOD_POST);

		// add a label
		panel.add(selectLabel);
		// add fileUpload widget
		panel.add(fileUpload);
		// add a button to upload the file
		panel.add(uploadButton);

		uploadButton.addClickHandler(new ClickHandler() {
			public void onClick(ClickEvent event) {
				// get the filename to be uploaded
				String filename = fileUpload.getFilename();
				if (filename.length() == 0) {
					Window.alert("No File Specified!");
				} else {
					// submit the form
					form.submit();
				}
			}
		});

		// Add form to the root panel.
		form.add(panel);

		RootPanel.get("formWidgets").add(form);
		
		  // Create a menu bar
		   MenuBar menu = new MenuBar();
		   menu.setAutoOpen(true);
		   menu.setWidth("100px");
		   menu.setAnimationEnabled(true);


		   // Create the file menu
		   MenuBar fileMenu = new MenuBar(true);
		   fileMenu.setAnimationEnabled(true);
		  
		   fileMenu.addItem("Toogle Button1 visibility", new ScheduledCommand() {
			      public void execute() {
			    	button1.setVisible( !button1.isVisible() ); 
			      }
			   });

		   
		   fileMenu.addItem("New", new ScheduledCommand() {
		      public void execute() {
		        /* showSelectedMenuItem("New");*/
		      }
		   });
		   fileMenu.addItem("Open", new ScheduledCommand() {
		      public void execute() {
		       /*  showSelectedMenuItem("Open");*/
		      }
		   });
		   fileMenu.addSeparator();
		   fileMenu.addItem("Exit", new ScheduledCommand() {
		      public void execute() {
		    	  
		      }
		   });
		   
		   menu.addItem(new MenuItem("File", fileMenu));
		   RootPanel.get("formWidgets").add(menu);
		   
		   
		// Create an empty tab panel
		TabPanel tabPanel = new TabPanel();

		// create contents for tabs of tabpanel
		Label label1 = new Label("This is contents of TAB 1");
		label1.setHeight("200");
		Label label2 = new Label("This is contents of TAB 2");
		label2.setHeight("200");
		Label label3 = new Label("This is contents of TAB 3");
		label3.setHeight("200");
		
		// create titles for tabs
		String tab1Title = "TAB 1";
		String tab2Title = "TAB 2";
		String tab3Title = "TAB 3";

		// create tabs
		tabPanel.add(label1, tab1Title);
		tabPanel.add(label2, tab2Title);
		tabPanel.add(label3, tab3Title);
		tabPanel.selectTab(0);
		
		// Add the widgets to the root panel.
		RootPanel.get("GWTComplexWidgetsandLayotPanels").add(tabPanel);
		
		
		// Use dialog box
		Button b = new Button("Click me to show dialog");
		b.addClickHandler(new ClickHandler() {
			public void onClick(ClickEvent event) {
				// Instantiate the dialog box and show it.
				MyDialog myDialog = new MyDialog();

				int left = Window.getClientWidth() / 2;
				int top = Window.getClientHeight() / 2;
				myDialog.setPopupPosition(left, top);
				myDialog.show();
			}
		});

		RootPanel.get().add(b);

		// mouse over event example
		button1.addMouseOverHandler(new MouseOverHandler() {
			public void onMouseOver(MouseOverEvent arg0) {
				
				button1.getElement().getStyle().setProperty("background", "green");
			}
		});
		/********************/

		final Button sendButton = new Button(messages.sendButton() + ".");
		final TextBox nameField = new TextBox();
		nameField.setText(messages.nameField());
		final Label errorLabel = new Label();

		// We can add style names to widgets
		sendButton.addStyleName("sendButton");

		// Add the nameField and sendButton to the RootPanel
		// Use RootPanel.get() to get the entire body element
		RootPanel.get("nameFieldContainer").add(nameField);
		RootPanel.get("sendButtonContainer").add(sendButton);
		RootPanel.get("errorLabelContainer").add(errorLabel);

		// Focus the cursor on the name field when the app loads
		nameField.setFocus(true);
		nameField.selectAll();

		// Create the popup dialog box
		final DialogBox dialogBox = new DialogBox();
		dialogBox.setText("Remote Procedure Call");
		dialogBox.setAnimationEnabled(true);
		final Button closeButton = new Button("Close this");
		// We can set the id of a widget by accessing its Element
		closeButton.getElement().setId("closeButton");
		final Label textToServerLabel = new Label();
		final HTML serverResponseLabel = new HTML();
		VerticalPanel dialogVPanel = new VerticalPanel();
		dialogVPanel.addStyleName("dialogVPanel");
		dialogVPanel.add(new HTML("<b>Sending name to the server:</b>"));
		dialogVPanel.add(textToServerLabel);
		dialogVPanel.add(new HTML("<br><b>Server replies:</b>"));
		dialogVPanel.add(serverResponseLabel);
		dialogVPanel.setHorizontalAlignment(VerticalPanel.ALIGN_RIGHT);
		dialogVPanel.add(closeButton);
		dialogBox.setWidget(dialogVPanel);

		// Add a handler to close the DialogBox
		closeButton.addClickHandler(new ClickHandler() {
			public void onClick(ClickEvent event) {
				dialogBox.hide();
				sendButton.setEnabled(true);
				sendButton.setFocus(true);
			}
		});

		// Create a handler for the sendButton and nameField
		class MyHandler implements ClickHandler, KeyUpHandler {
			/**
			 * Fired when the user clicks on the sendButton.
			 */
			public void onClick(ClickEvent event) {
				sendNameToServer();
			}

			/**
			 * Fired when the user types in the nameField.
			 */
			public void onKeyUp(KeyUpEvent event) {
				if (event.getNativeKeyCode() == KeyCodes.KEY_ENTER) {
					sendNameToServer();
				}
			}

			/**
			 * Send the name from the nameField to the server and wait for a
			 * response.
			 */
			private void sendNameToServer() {
				// First, we validate the input.
				errorLabel.setText("");
				String textToServer = nameField.getText();
				if (!FieldVerifier.isValidName(textToServer)) {
					errorLabel.setText("Please enter at least four characters");
					return;
				}

				// Then, we send the input to the server.
				sendButton.setEnabled(false);
				textToServerLabel.setText(textToServer);
				serverResponseLabel.setText("");
				greetingService.greetServer(textToServer,
						new AsyncCallback<String>() {
							public void onFailure(Throwable caught) {
								// Show the RPC error message to the user
								dialogBox
										.setText("Remote Procedure Call - Failure");
								serverResponseLabel
										.addStyleName("serverResponseLabelError");
								serverResponseLabel.setHTML(SERVER_ERROR);
								dialogBox.center();
								closeButton.setFocus(true);
							}

							public void onSuccess(String result) {
								dialogBox.setText("Remote Procedure Call =)");
								serverResponseLabel
										.removeStyleName("serverResponseLabelError");
								serverResponseLabel.setHTML(result);
								dialogBox.center();
								closeButton.setFocus(true);
							}
						});
			}
		}

		// Add a handler to send the name to the server
		MyHandler handler = new MyHandler();
		sendButton.addClickHandler(handler);
		nameField.addKeyUpHandler(handler);
	}
	
	private static class MyDialog extends DialogBox {

	      public MyDialog() {
	         // Set the dialog box's caption.
	         setText("My First Dialog");

	         // Enable animation.
	         setAnimationEnabled(true);

	         // Enable glass background.
	         setGlassEnabled(true);

	         // DialogBox is a SimplePanel, so you have to set its widget 
	         // property to whatever you want its contents to be.
	         Button ok = new Button("OK");
	         ok.addClickHandler(new ClickHandler() {
	            public void onClick(ClickEvent event) {
	               MyDialog.this.hide();
	            }
	         });

	         Label label = new Label("This is a simple dialog box.");

	         VerticalPanel panel = new VerticalPanel();
	         panel.setHeight("100");
	         panel.setWidth("300");
	         panel.setSpacing(10);
	         panel.setHorizontalAlignment(HasHorizontalAlignment.ALIGN_CENTER);
	         panel.add(label);
	         panel.add(ok);

	         setWidget(panel);
	      }
	   }

	public static native void logToConsole() /*-{
		console.log("some text");
		
		if(aobj && aobj.val){
			console.log(aobj.val);
		}else{
			console.log("no values " );
		}
	}-*/;
	
}
