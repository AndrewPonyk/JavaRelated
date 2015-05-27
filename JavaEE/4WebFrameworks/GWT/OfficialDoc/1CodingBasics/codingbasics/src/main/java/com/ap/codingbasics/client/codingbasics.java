package com.ap.codingbasics.client;

import java.util.Date;

import com.ap.codingbasics.client.overlaytypes.OverlayExamples;
import com.ap.codingbasics.client.timeutilsrpc.TimeUtilsService;
import com.ap.codingbasics.client.timeutilsrpc.TimeUtilsServiceAsync;
import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.logical.shared.SelectionEvent;
import com.google.gwt.event.logical.shared.SelectionHandler;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.i18n.client.DateTimeFormat;
import com.google.gwt.i18n.client.NumberFormat;
import com.google.gwt.user.client.Command;
import com.google.gwt.user.client.History;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.RichTextArea;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwt.user.client.ui.TabPanel;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.impl.RichTextAreaImpl;

/**
 * Entry point classes define <code>onModuleLoad()</code>.
 */
public class codingbasics implements EntryPoint {

	static final int TIMEOUT = 5; // 5 second timeout

	// A keeper of the timer instance in case we need to cancel it
	private Timer timeoutTimer = null;

	// An indicator when the computation should quit
	private boolean abortFlag = false;

	private final String myInstanceField = "My Java Instance field";

	private static final String SERVER_ERROR = "An error occurred while "
      + "attempting to contact the server. Please check your network "
      + "connection and try again.";

  private final Messages messages = GWT.create(Messages.class);

  OverlayExamples overlayExamples = new OverlayExamples();

  private TabPanel tabPanel;

  private FlowPanel sheduleMessagesPanel;

  private TimeUtilsServiceAsync timeUtilsAsync = GWT.create(TimeUtilsService.class);

  // Used deferred binding. look at Popup.gwt.xml

	private void learnDeferredBinding() {
		RichTextArea area = new RichTextArea();
		RichTextAreaImpl impl = GWT.create(RichTextAreaImpl.class);
		String deferredText ="<b>Using implementation :</b>" + impl.getClass().toString() + "<br><hr>";
		deferredText += "<b>Text</b> area class has <b>field</b> 'private RichTextAreaImpl impl = GWT.create(RichTextAreaImpl.class);'"
				+ "and this is example of DEFERRED Binding , look in the RichText.gwt.xml";
		area.setHTML(deferredText);
		area.setWidth("700px");

		RootPanel.get().add(area);
	}

  private void learnDelayedLogic(RootPanel root) {
		sheduleMessagesPanel = new FlowPanel();

		// GWT provides three classes that you can use to defer running code
		// until
		// a later point in time: 1 Timer, 2 DeferredCommand, and
		// 3 IncrementalCommand.

		//////////////// 1 Using Timer and RPC
		timeoutTimer = new Timer() {
			public void run() {
				Window.alert("Timeout expired.");
				timeoutTimer = null;
				abortFlag = true;
			}
		};

		// (re)Initialize the abort flag and start the timer.
		abortFlag = false;
		timeoutTimer.schedule(TIMEOUT * 1000); // timeout is in milliseconds

		timeUtilsAsync.getTime(new AsyncCallback<String>() {

			@Override
			public void onSuccess(String arg0) {
				cancelTimer();
				if (abortFlag) {
					// Timeout already occurred. discard result
					return;
				}
				Window.alert("RPC returned: " + arg0);
			}

			@Override
			public void onFailure(Throwable arg0) {
				Window.alert("RPC Failed:" + arg0);
				cancelTimer();
			}
		});
		////////////////

		/////////////////////2 DeferredCommand
		// Set the focus on the widget after setup completes.
		Scheduler.get().scheduleDeferred(new Command() {
			public void execute() {
				//dataEntry.setFocus();
				Window.alert("DeferredCommand");
			}
		});
		  ///////////////////


		/////////////// 3
		/// Avoiding Slow Script Warnings: the IncrementalCommand class
		// dont understand this .
		///////////////


		root.add(sheduleMessagesPanel);
	}

	/*
		XML types
		The XML types provided by GWT can be found in the com.google.gwt.xml.client package. In order to use these in your application, you’ll need to add the following <inherits> tag to your module XML file:

		<inherits name="com.google.gwt.xml.XML" />

		Parsing XML

		To demonstrate how to parse XML with GWT, we’ll use the following XML
		document that contains an email message:

		<?xml version="1.0" ?>
		<message>
		  <header>
		    <to displayName="Richard" address="rick@school.edu" />
		    <from displayName="Joyce" address="joyce@website.com" />
		    <sent>2007-05-12T12:03:55Z</sent>
		    <subject>Re: Flight info</subject>
		  </header>
		  <body>I'll pick you up at the airport at 8:30.  See you then!</body>
		</message>

			*Suppose that you’re writing an email application and need to extract the name of the sender, the subject line, and the message body from the XML. Here is sample code that will
			do just that (we’ll explain the code in just a bit):

		private void parseMessage(String messageXml) {
		  try {
		    // parse the XML document into a DOM
		    Document messageDom = XMLParser.parse(messageXml);

		    // find the sender's display name in an attribute of the <from> tag
		    Node fromNode = messageDom.getElementsByTagName("from").item(0);
		    String from = ((Element)fromNode).getAttribute("displayName");
		    fromLabel.setText(from);

		    // get the subject using Node's getNodeValue() function
		    String subject = messageDom.getElementsByTagName("subject").item(0).getFirstChild().getNodeValue();
		    subjectLabel.setText(subject);

		    // get the message body by explicitly casting to a Text node
		    Text bodyNode = (Text)messageDom.getElementsByTagName("body").item(0).getFirstChild();
		    String body = bodyNode.getData();
		    bodyLabel.setText(body);

		  } catch (DOMException e) {
		    Window.alert("Could not parse XML document.");
		  }
		}

		Building an XML document
	In addition to parsing existing documents, the GWT XML types can also be used
	to create and modify XML. To create a new XML document, call the static
	createDocument() method of the XMLParser class. You can then use the methods of the
	resulting Document to create elements, text nodes, and other XML nodes. These
	nodes can be added to the DOM tree using the appendChild(Node) and
	insertBefore(Node, Node) methods. Node also has methods for replacing and removing child
	nodes (replaceChild(Node, Node) and removeChild(Node), respectively).

			*/

	// Stop the timeout timer if it is running
	private void cancelTimer() {
		if (timeoutTimer != null) {
			timeoutTimer.cancel();
			timeoutTimer = null;
		}
	}

	private void learnJSNI(RootPanel root){
		Button jsniExample = new Button("Run JSNI Example");

		jsniExample.addClickHandler(new ClickHandler() {

			@Override
			public void onClick(ClickEvent arg0) {
				Window.alert("JSNI is great " + doubleN(3));
				Window.alert("Your browser is : " + getUserAgent());
			}
		});

		root.add(jsniExample);
	}

	// Accessing Java Methods and Fields from JavaScript
	//Invoking Java methods from JavaScript

	/*Calling Java methods from JavaScript is somewhat similar to calling
	Java methods from C code in JNI. In particular, JSNI borrows the
	JNI mangled method signature approach to distinguish among overloaded methods.
	JavaScript calls into Java methods are of the following form:

	[instance-expr.]@class-name::method-name(param-signature)(arguments)
	*
	*
	*/


	private void callJavaFromJavascript(RootPanel root){
		Button callJavaFromJS = new Button("callJavaFromJS");

		callJavaFromJS.addClickHandler(new ClickHandler() {

			@Override
			public void onClick(ClickEvent arg0) {
				jsniNative();
				jsniCallJavaMethod();
			}
		});

		root.add(callJavaFromJS);
	}


	private void learnDateAndNumberFormatting(VerticalPanel vp) {
		NumberFormat fmt = NumberFormat.getDecimalFormat();
		double value = 12345.6789;
		String formatted = fmt.format(value);

		double value1 = NumberFormat.getDecimalFormat().parse("12345.6789");

		//6 digits of precission on right hand, result will be 12345.334456
		double value2 = 12345.33445566;
		String formatted2 = NumberFormat.getFormat(".000000").format(value2);

		vp.setBorderWidth(1);
		vp.add(new Label("Formatting Numbers and dates"));
		vp.add(new Label(formatted));
		vp.add(new Label(formatted2));

		//
		/// Formatting Dates
		Date today = new Date();
		// prints Tue Dec 18 12:01:26 GMT-500 2007 in the default locale.
		vp.add(new Label(today.toString()));

	    // prints 12/18/07 in the default locale
		vp.add(new Label(DateTimeFormat.getShortDateFormat().format(today)));

	    // prints December 18, 2007 in the default locale
		vp.add(new Label(DateTimeFormat.getLongDateFormat().format(today)));

	    // prints 12:01 PM in the default locale
		vp.add(new Label(DateTimeFormat.getShortTimeFormat().format(today)));

	    // prints 12:01:26 PM GMT-05:00 in the default locale
		vp.add(new Label(DateTimeFormat.getLongTimeFormat().format(today)));

	    // prints Dec 18, 2007 12:01:26 PM in the default locale
		vp.add(new Label(DateTimeFormat.getMediumDateTimeFormat().format(today)));
	}
  /**
   * This is the entry point method.
   */
	public void onModuleLoad() {
		GWT.log("mesage");
		tabPanel = new TabPanel();

		tabPanel.add(new HTML("<h1>Page 0 Content: Llamas</h1>"), " Page 0 ");
		tabPanel.add(new HTML("<h1>Page 1 Content: Alpacas</h1>"), " Page 1 ");
		tabPanel.add(new HTML("<h1>Page 2 Content: Camels</h1>"), " Page 2 ");

		tabPanel.addSelectionHandler(new SelectionHandler<Integer>() {
			public void onSelection(SelectionEvent<Integer> event) {
				History.newItem("page" + event.getSelectedItem());
			}
		});

		History.addValueChangeHandler(new ValueChangeHandler<String>() {
			public void onValueChange(ValueChangeEvent<String> event) {
				String historyToken = event.getValue();

				// Parse the history token
				try {
					if (historyToken.substring(0, 4).equals("page")) {
						String tabIndexToken = historyToken.substring(4, 5);
						int tabIndex = Integer.parseInt(tabIndexToken);
						// Select the specified tab panel
						tabPanel.selectTab(tabIndex);
					} else {
						// possible bug !!! if we always will set 0
						//tabPanel.selectTab(0);
					}

				} catch (IndexOutOfBoundsException e) {
					//tabPanel.selectTab(0);
				}
			}
		});

		tabPanel.selectTab(0);
		RootPanel.get().add(tabPanel);

		////////////
		VerticalPanel vp = new VerticalPanel();
		learnDateAndNumberFormatting(vp);
		RootPanel.get().add(vp);

		////
		learnDelayedLogic(RootPanel.get());

		////
		learnJSNI(RootPanel.get());

		int k = 10;
		k = k * 2;
		Window.alert("from IDEA 1114444" + k);

		//
		callJavaFromJavascript(RootPanel.get());

		/// Examples with Overlays
		overlayExamples.parsingJsonStringToOrerlayArray();

		overlayExamples.parsingJSObject();

		// example of deferred binding
		learnDeferredBinding();
	}


	private String getParameterFromUrl(String url, int n){

		String result = url.substring(1,n);
		return result;
	}

	private native int doubleN(int n)/*-{
		return n * 2;
	}-*/;

	public static native String getUserAgent() /*-{
		return navigator.userAgent.toLowerCase();
	}-*/;

	public native void jsniNative()/*-{
		alert(this.@com.ap.codingbasics.client.codingbasics::myInstanceField);
	}-*/;

	public native void jsniCallJavaMethod()/*-{
		var result = this.@com.ap.codingbasics.client.codingbasics::getParameterFromUrl(Ljava/lang/String;I)($wnd.location.href, 10);
		$wnd.alert("Result from java METHOD : " + result)
	}-*/;

	public native void jsniSomeProblemsWithLong()/*-{

	}-*/;
}