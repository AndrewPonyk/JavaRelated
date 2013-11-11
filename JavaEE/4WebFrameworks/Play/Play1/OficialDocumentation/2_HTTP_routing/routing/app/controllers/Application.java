package controllers;

import play.*;
import play.mvc.*;

import java.util.*;

import models.*;

public class Application extends Controller {
    
    @Before
    static void doSome(){
    
  	System.out.println("doSome");
    }
    public static void index() {
        render();
    }

    public static void show(String id,String name){
	System.out.println("Id :"+id);
        System.out.println("Name :"+name);
      render(id,name);
    }
	public static void showXML(){
		
		String id="8888";
		render(id);	
 	}

}
