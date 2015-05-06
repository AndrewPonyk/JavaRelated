package com.documentation.client;

import java.util.ArrayList;

import com.google.gwt.user.client.rpc.AsyncCallback;

public interface StockPriceServiceAsync {
	void getPrices(String[] symbols, AsyncCallback<ArrayList<StockPrice>> callback);
	void getJSON(Integer n, AsyncCallback<String> callback);
}
