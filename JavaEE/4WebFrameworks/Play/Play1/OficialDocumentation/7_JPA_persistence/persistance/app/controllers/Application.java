package controllers;

import play.*;
import play.db.jpa.JPA;
import play.jobs.OnApplicationStart;
import play.mvc.*;

import java.util.*;

import javax.persistence.Query;

import models.*;

public class Application extends Controller {

	
    public static void index() {
    	renderArgs.put("Items", Item.findAll());
 

        Query query = JPA.em().createQuery("select i from Item AS i where i.count<5");
        List<Item> articles = query.getResultList();
    
        System.out.println(articles.size());
    	
        
        
        //Item.find("byCountLessThan", 2L).fetch().size();  // dont work (i dont know why )
        
        //Item.find("byCount", 2).fetch(); // doesnt work with numerical
        
         Item.find("byName", "Item1").fetch(); // byName - work for strings 
        
    	
        render();
    }

}