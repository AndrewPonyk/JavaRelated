package com.ap.restresource;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import javax.ws.rs.ApplicationPath;
import javax.ws.rs.core.Application;

@ApplicationPath("/jaxrs")
public class RestApplicationConfig extends Application{
	@Override
	public Set<Class<?>> getClasses() {
		return new HashSet<Class<?>>();
		
	}
}
