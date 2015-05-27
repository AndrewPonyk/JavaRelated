package com.documentation.client;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;

import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.core.client.JsonUtils;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.dom.client.KeyDownHandler;
import com.google.gwt.http.client.Request;
import com.google.gwt.http.client.RequestBuilder;
import com.google.gwt.http.client.RequestCallback;
import com.google.gwt.http.client.RequestException;
import com.google.gwt.http.client.Response;
import com.google.gwt.i18n.client.DateTimeFormat;
import com.google.gwt.i18n.client.NumberFormat;
import com.google.gwt.json.client.JSONObject;
import com.google.gwt.jsonp.client.JsonpRequestBuilder;
import com.google.gwt.user.client.Random;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.FlexTable;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.impl.FocusImpl;

/**
 * Entry point classes define <code>onModuleLoad()</code>.
 */
public class StockWatcherGWT implements EntryPoint {
	private static final int REFRESH_INTERVAL = 10000;
	private static final String JSON_URL = GWT.getModuleBaseURL() + "prices.json";
	private static final String JSONP_URL = "http://127.0.0.1:1119?q=abc";

	final double MAX_PRICE = 100.0; // $100.00

	private VerticalPanel mainPanel = new VerticalPanel();
	private HorizontalPanel addPanel = new HorizontalPanel();
	private Label lastUpdatedLabel = new Label();
	private FlexTable stocksFlexTable = new FlexTable();
	private TextBox newSymbolTextBox = new TextBox();
	private Button addStockButton = new Button("Add");
	private ArrayList<StockPrice> stocks = new ArrayList<StockPrice>();

	private StockPriceServiceAsync stockPriceSvc = GWT.create(StockPriceService.class);

	private StockWatcherConstants constants = GWT.create(StockWatcherConstants.class);
	private StockWatcherMessages messages = GWT.create(StockWatcherMessages.class);

	/**
	 * This is the entry point method.
	 */
	public void onModuleLoad() {
		studyDeferredBinding();
		console(GWT.getModuleBaseURL());

		RootPanel.get("appTitle").add(new Label(this.constants.stockWatcher()));

		if (RootPanel.get("stockList") != null) {
			// Create table for stock data.
			stocksFlexTable.setText(0, 0, constants.symbol());
			stocksFlexTable.setText(0, 1, constants.price());
			stocksFlexTable.setText(0, 2, constants.change());
			stocksFlexTable.setText(0, 3, constants.remove());
			// Add styles to elements in the stock list table.
			stocksFlexTable.getRowFormatter().addStyleName(0, "watchListHeader");
			stocksFlexTable.addStyleName("watchList");
			stocksFlexTable.getCellFormatter().addStyleName(0, 1, "watchListNumericColumn");
			stocksFlexTable.getCellFormatter().addStyleName(0, 2, "watchListNumericColumn");
			stocksFlexTable.getCellFormatter().addStyleName(0, 3, "watchListRemoveColumn");

			// Assemble Add Stock panel.
			addPanel.add(newSymbolTextBox);
			addPanel.add(addStockButton);
			addPanel.addStyleName("addPanel");

			// Assemble Main panel.
			mainPanel.add(stocksFlexTable);
			mainPanel.add(addPanel);
			mainPanel.add(lastUpdatedLabel);

			// Add to HTML host page
			RootPanel.get("stockList").add(mainPanel);

			//bel
			newSymbolTextBox.setFocus(true);

			addStockButton.addClickHandler(new ClickHandler() {

				@Override
				public void onClick(ClickEvent event) {
					addStock();
				}
			});

			newSymbolTextBox.addKeyDownHandler(new KeyDownHandler() {

				@Override
				public void onKeyDown(KeyDownEvent event) {
					if(event.getNativeKeyCode() == KeyCodes.KEY_ENTER){
						//Window.alert("Enter");
						addStock();
					}
				}
			});

			Timer refreshTimer = new Timer() {

				@Override
				public void run() {
					refreshWatchList();
				}
			};
			refreshTimer.scheduleRepeating(REFRESH_INTERVAL);
		} else{
			RootPanel.get().add(new Label("Wrong integration, add 'stockList' element"));
		}


		Button gwtGetJsonButton = new Button("Get JSON");
		RootPanel.get("buttons").add(gwtGetJsonButton);
		gwtGetJsonButton.addClickHandler(new ClickHandler() {

			@Override
			public void onClick(ClickEvent event) {
				AsyncCallback<String> callback = new AsyncCallback<String>() {

					@Override
					public void onSuccess(String result) {
						Window.alert(result);

					}

					@Override
					public void onFailure(Throwable caught) {
						// it is like catch, we can throw exception in *impl class and it will be caught here
					}
				};
				stockPriceSvc.getJSON(10, callback);
			}
		});

		Button getJSONWithoutRPC = new Button("getJSONWithoutRPC");
		RootPanel.get("buttons").add(getJSONWithoutRPC);
		getJSONWithoutRPC.addClickHandler(new ClickHandler(
				) {
			@Override
			public void onClick(ClickEvent event) {
				requestJSON();
			}
		});

		Button getJSONP = new Button("Get JSONP");
		RootPanel.get("jsonp").add(getJSONP);
		getJSONP.addClickHandler(new ClickHandler() {

			@Override
			public void onClick(ClickEvent event) {
				 JsonpRequestBuilder builder = new JsonpRequestBuilder();

				 builder.requestObject(JSONP_URL, new AsyncCallback<JsArray<StockData>>() {
					 @Override
					public void onSuccess(JsArray<StockData> result) {
						 Window.alert("Pobeda )))" );
						 Window.alert("First element of json : " + result.get(0).getSymbol());
					}

					 @Override
					public void onFailure(Throwable caught) {
						 Window.alert(caught.getMessage());
						 caught.printStackTrace();
					}
				});
			}
		});
	}

	private void addStock() {
		final String symbol = newSymbolTextBox.getText().toUpperCase().trim();
		// Stock code must be between 1 and 10 chars that are numbers, letters,
		// or dots.
		if (!symbol.matches("^[0-9A-Z&#92;&#92;.]{1,10}$")) {
			Window.alert(messages.invalidSymbol(symbol));
			newSymbolTextBox.selectAll();
			return;
		}

		// Don't add the stock if it's already in the table.
		for(StockPrice item : stocks){
			if(item.getSymbol().equals(symbol)){
				Window.alert("Can't add duplicate");
				return;
			}
		}

		int row = stocksFlexTable.getRowCount();
		StockPrice price = new StockPrice(symbol, Random.nextDouble() * MAX_PRICE, 0);

		stocks.add(price);
		stocksFlexTable.setText(row, 0, symbol);
		stocksFlexTable.setText(row, 1, price.getPrice() + "");
		stocksFlexTable.getCellFormatter().addStyleName(row, 1, "watchListNumericColumn");
		stocksFlexTable.getCellFormatter().addStyleName(row, 2, "watchListNumericColumn");
		stocksFlexTable.getCellFormatter().addStyleName(row, 3, "watchListRemoveColumn");

		Button removeStockButton = new Button("x");
		removeStockButton.addStyleDependentName("remove");
		removeStockButton.addClickHandler(new ClickHandler() {
			public void onClick(ClickEvent event) {
				int removedIndex = findStock(symbol);
				stocks.remove(removedIndex);
				stocksFlexTable.removeRow(removedIndex + 1);
			}
		});
		stocksFlexTable.setWidget(row, 3, removeStockButton);
	}

	private int findStock(String symbol){
		for(int i = 0; i < stocks.size(); i++){
			if(stocks.get(i).getSymbol().equals(symbol)){
				return i;
			}
		}
		return -1;
	}

	private void refreshWatchList() {
		final double MAX_PRICE_CHANGE = 0.02; // +/- 2%

		//StockPrice[] prices = new StockPrice[stocks.size()];

		for (int i = 0; i < stocks.size(); i++) {

			double change = stocks.get(i).getPrice() * MAX_PRICE_CHANGE
					* (Random.nextDouble() * 2.0 - 1.0);
			stocks.get(i).setPrice(stocks.get(i).getPrice() + change);
			stocks.get(i).setChange(change);
			//prices[i] = new StockPrice(stocks.get(i).getSymbol(), stocks.get(i).getPrice() + change, change);
		}

		updateTable(stocks);

		DateTimeFormat dateFormat = DateTimeFormat.getFormat(DateTimeFormat.PredefinedFormat.DATE_TIME_MEDIUM);
		 lastUpdatedLabel.setText("Last update : "
			        + dateFormat.format(new Date()));
	}

	private void updateTable(List<StockPrice> stocks) {
		for (int i = 0; i < stocks.size(); i++) {
			updateTable(stocks.get(i));
		}

	}
	private void updateTable(StockPrice stock) {
		if (!stocks.contains(stock)) {
			return;
		}

		int row = stocks.indexOf(stock) + 1;

		// Format the data in the Price and Change fields.
		String priceText = NumberFormat.getFormat("#,##0.00").format(
				stock.getPrice());
		NumberFormat changeFormat = NumberFormat.getFormat("+#,##0.00;-#,##0.00");
		String changeText = changeFormat.format(stock.getChange());
		String changePercentText = changeFormat.format(stock.getChangePercent());

		// Populate the Price and Change fields with new data.
		stocksFlexTable.setText(row, 1, priceText);
		stocksFlexTable.setText(row, 2, changeText + " (" + changePercentText + "%)");
		if(stock.getChangePercent() < 0){
			stocksFlexTable.getCellFormatter().removeStyleName(row, 2, "positiveChange");
			stocksFlexTable.getCellFormatter().addStyleName(row, 2, "negativeChange");
		}else{
			stocksFlexTable.getCellFormatter().removeStyleName(row, 2, "negativeChange");
			stocksFlexTable.getCellFormatter().addStyleName(row, 2, "positiveChange");
		}
	}

	public final native void console(String s)/*-{
		console.log(s);
	}-*/;

	public void requestJSON() {
		// we put prices.json in stocowatcher folder, be careful ,because this folder is regenerated during compilation ))
		RequestBuilder builder = new RequestBuilder(RequestBuilder.GET, JSON_URL);
		try {
			Request request = builder.sendRequest(null, new RequestCallback() {
				public void onError(Request request, Throwable exception) {
					console("Couldn't retrieve JSON");
				}

				public void onResponseReceived(Request request, Response response) {
					if (200 == response.getStatusCode()) {
						//updateTable(JsonUtils.<JsArray<StockData>> safeEval(response.getText()));
						console("Ok , we have 200 !!!");
						//JsonUtils.<JsArray<StockData>>safeEval(response.getText());
						JSONObject pricesJson = new JSONObject(JsonUtils.safeEval(response.getText()));
						console(pricesJson.keySet().size()+"");

						Set<String> keys = pricesJson.keySet();

						for(String item : keys){
							JSONObject price = (JSONObject) pricesJson.get(item);
							console("StockPrice  : " + price.get("symbol") + ", price = " + price.get("price"));
						}
					} else {
						console("Couldn't retrieve JSON ("
								+ response.getStatusText() + ")");
					}
				}
			});
		} catch (RequestException e) {
			console("In the catch");
			e.printStackTrace();
		}
	}

	public void studyDeferredBinding(){
		FocusImpl create = GWT.create(FocusImpl.class);
		Window.alert(create.getClass() + "");
	}
}


// for JSONP we need some server , i used Echo java server


/*package networking;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

public class EchoHTTPServerUsingSocketsForJSONP {

	public static void main(String[] args) {
		try {
			System.out.println("Create server");

			BufferedReader is; // inputStream from web browser
			PrintWriter os; // outputStream to web browser
			String request; // Request from web browser

			ServerSocket serverSocket = new ServerSocket(1119);
			Socket s = null;
			while (true) {
				try {
					s = serverSocket.accept();
					String webServerAddress = s.getInetAddress().toString();
					System.out.println("Accepted connection from "
							+ webServerAddress);
					is = new BufferedReader(new InputStreamReader(
							s.getInputStream()));

					request = is.readLine();
					System.out.println("Server recieved request from client: "
							+ request);
					String callback = request.replaceAll("(.*(callback=([\\w\\.]+)).*)|.*", "$3");
					System.out.println(callback);

					os = new PrintWriter(s.getOutputStream(), true);
					os.println("HTTP/1.0 200");
					os.println("Content-Type:application/json;charset=utf-8");
					os.println("Server-name: myserver");
					String response = "[{\"symbol\":\"ABC\",\"price\":53.554212,\"change\":0.584011}]";

					if(callback != null){
						response = callback + "(" + response + ");";
					}
					os.println("Content-length: " + response.length());
					os.println("");
					os.println(response);
					os.flush();
					os.close();
					s.close();
				} catch (IOException e) {
					System.out.println("Failed to send response to client: "
							+ e.getMessage());
				}
			}

		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}*/