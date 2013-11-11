package controllers;

import play.*;
import play.mvc.*;

import java.util.*;



public class Application extends Controller {

    
	public static void index() {
        render();
    }
	
	
	public static void devices(Integer id){
		id-- ; //numeration from zero
		//create 'Model' =)
		List<String>  devices=new ArrayList<String>();
		devices.add("1,Sony,Sony k550i");
		devices.add("2,HTC,HTC WildFire");
		devices.add("3,Samsung,Samsung Galaxy");
		
		//get data
		HashMap<String, String> result=new HashMap<String, String>();
		if(id!=null && devices.size()>id && id>-1){
			result.put("id", devices.get(id).split(",")[0]);
			result.put("name", devices.get(id).split(",")[1]);
			result.put("details", devices.get(id).split(",")[2]);
		}else{
			result.put("id", "wrong id");
			result.put("name", "wrong id");
			result.put("details", "wrong id");
		}

		renderTemplate("Application/devices.xml",result);
	}
}
