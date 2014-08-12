package org.som.simple.client;

import com.google.gwt.core.client.GWT;
import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.CssResource;

public interface LoginResources extends ClientBundle {
	//public final static LoginResources INSTANCE = GWT.create(LoginResources.class);

	
	/**
   * Sample CssResource.
   */
   public interface MyCss extends CssResource {
      String blackText();

      String redText();

      String loginButton();

      String box();

      String background();
   }

   @Source("Login.css")
   MyCss style();
}