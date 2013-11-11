package controllers;

import play.*;
import play.mvc.*;

import java.util.*;

import models.*;

public class Application extends Controller {

	@Before
	static void addDefaults(){ 
		
		renderArgs.put("blogTitle", Play.configuration.getProperty("blog.title"));
	    renderArgs.put("blogBaseline", Play.configuration.getProperty("blog.baseline"));
	}
	
    public static void index() {
    	  Post frontPost = Post.find("order by postedAt desc").first();
          List<Post> olderPosts = Post.find(
              "order by postedAt desc"
          ).from(1).fetch(10);
          
          //just study how to display list on page
          List<User> allUsers=User.findAll();
                
          render(frontPost, olderPosts,allUsers);
    }
    
    public static void show(Long id) {
        Post post=Post.findById(id);
        System.out.println(post);
        render(post);
    	
    }
    
}