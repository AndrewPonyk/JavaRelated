package com.ap.codingbasics.client.timeutilsrpc;

import com.google.gwt.user.client.rpc.RemoteService;
import com.google.gwt.user.client.rpc.RemoteServiceRelativePath;

@RemoteServiceRelativePath("timeutilsservice")
public interface TimeUtilsService extends RemoteService {
	public String getTime();
}
