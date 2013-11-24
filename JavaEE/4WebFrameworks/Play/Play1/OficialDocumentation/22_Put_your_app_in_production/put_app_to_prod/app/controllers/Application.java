package controllers;

import play.*;
import play.mvc.*;

import java.util.*;

import models.*;

public class Application extends Controller {

    public static void index() {
    	int result=1;
    	for(int i=0;i<10;i++){
    		result*=2;
    	}
    	for(String item : request.headers.keySet()){
    		System.out.println(request.headers.get(item).name);
    		System.out.println(request.headers.get(item).value());
    		System.out.println("##################");
    	}
        render(result);
    }

}