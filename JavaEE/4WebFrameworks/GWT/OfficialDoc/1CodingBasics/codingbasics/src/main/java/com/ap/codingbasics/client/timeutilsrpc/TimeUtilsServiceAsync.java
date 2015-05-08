package com.ap.codingbasics.client.timeutilsrpc;

import com.google.gwt.user.client.rpc.AsyncCallback;

public interface TimeUtilsServiceAsync {

	void getTime(AsyncCallback<String> callback);
}
