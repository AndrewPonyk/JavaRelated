package controllers;

import play.*;
import play.cache.Cache;
import play.mvc.*;
import play.mvc.Scope.Session;

import java.util.*;


public class Application extends Controller {

    public static void index() {
    	//Cache.set("someObject", "valueOfSomeObject", "180s");
   
    	String val=Cache.get("someObject",String.class);
    	if(val==null){
    		Cache.set("someObject", "valueOfSomeObject","120s");
    		System.out.println("%%%%%%%%%%%%%%%%"+val);
    	}
        
    	render(val);
    }
    
    public static void showValueFromCache(){
    	if(Cache.get("someObject")==null){
    		renderText("expired value");
    	}else
    	renderText(Cache.get("someObject"));
    
    }

}