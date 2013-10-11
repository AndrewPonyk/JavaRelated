import org.junit.*;

import java.util.*;

import play.test.*;
import models.*;

public class BasicTest extends UnitTest {

    @Test
    public void aVeryImportantThingToTest() {
        assertEquals(2, 1 + 1);
        assertEquals("hello", "hello");
    }
    
    @Test
    public void createAndRetrieveUser(){
    	new User("bob@gmail.com", 
    			"secret", "Bob").save();
    	
    	User bob=User.find("byEmail", "bob@gmail.com")
    			.first();
    	
    	assertEquals("Bob", bob.fullname);
    }
    
    @Test
    public void fullTest() {
    	 // Create a new user and save it
        User bob = new User("bob@gmail.com", "secret", "Bob").save();
        
     // Create a new post
        Post bobPost = new Post(bob, "My first post", "Hello world").save();
        
        // Post a first comment
        new Comment(bobPost, "Jeff", "Nice post").save();
        new Comment(bobPost, "Tom", "I knew that !").save();
        
        // Retrieve all comments
        List<Comment> bobPostComments = Comment.find("byPost", bobPost).fetch();
        
        // Tests
        assertEquals(2, bobPostComments.size());
        
        for(int i=0;i<bobPostComments.size();i++){
        	System.out.println(bobPostComments.get(i).content );
        }
        
    }

}
