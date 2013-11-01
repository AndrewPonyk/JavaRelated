package controllers;

import play.*;
import play.mvc.*;

import java.util.*;

import models.*;

public class Application extends Controller {

    
	public static void index() {
        render();
    }
	
	
	public static void devices(Integer id){
		
		//create 'Model' =)
		List<String>  devices=new ArrayList<>();
		devices.add("1,Sony");
		devices.add("2,HTC");
		devices.add("3,Samsung");
		
		//get data
		HashMap<String, String> result=new HashMap<>();
		result.put("id", devices.get(id).split(",")[0]);
		result.put("name", devices.get(id).split(",")[1]);
		
		

		renderTemplate("Application/devices.xml",result);
	}
    
}




