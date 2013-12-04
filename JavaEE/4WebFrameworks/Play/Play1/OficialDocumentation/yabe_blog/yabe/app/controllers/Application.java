package controllers;

import play.*;
import play.mvc.*;

import java.util.*;

import models.*;

public class Application extends Controller {

    public static void index() {
    	
    	User bob =User.findById(1L);
    	System.out.println(bob.posts.size());
    	for(Post item : bob.posts){
    		System.out.println(item.content);
    	}
        render();
    }

}