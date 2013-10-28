package controllers;

import play.*;
import play.jobs.OnApplicationStart;
import play.mvc.*;

import java.util.*;

import models.*;

public class Application extends Controller {

	
    public static void index() {
    	renderArgs.put("Items", Item.findAll());
    	
    	//List<Item> items= Item.find("from Item where count =?1",10).fetch(); // dont work , i dont know why =)_

    	
    	//renderArgs.put("itemsWithCountLessThan10", Item.find("byCountLessThan", 2).fetch()); // doesnt work , i dont know why =)
    	renderArgs.put("itemsWithCountLessThan10", Item.find("byNameLike", "i%").fetch());
    	
        render();
    }

}