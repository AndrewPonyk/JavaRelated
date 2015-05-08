package com.ap.codingbasics.server.timeutilsrpc;

import java.util.Date;

import com.ap.codingbasics.client.timeutilsrpc.TimeUtilsService;
import com.google.gwt.thirdparty.json.JSONException;
import com.google.gwt.thirdparty.json.JSONObject;
import com.google.gwt.user.server.rpc.RemoteServiceServlet;

public class TimeUtilsServiceImpl extends RemoteServiceServlet implements TimeUtilsService {

	@Override
	public String getTime() {
		JSONObject json = new JSONObject();
		try {
			json.put("ab", "ccc");
		} catch (JSONException e) {
			e.printStackTrace();
		}

		Date date = new Date();
		return date.toGMTString() + json.toString();
	}

}
