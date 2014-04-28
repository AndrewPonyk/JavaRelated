package controllers;

import play.*;
import play.data.validation.Required;
import play.mvc.*;
import play.libs.MimeTypes;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.*;



public class Application extends Controller {

    public static void index() {
        render();
    }
    
    public static void sayHello(@Required String m,String surname){
    	if(validation.hasErrors()){
    		flash.error("Oops, please enter your name!");
    		index();
    	}

    	render(m,surname);
    }
    
    public static void processPost(File file, String param1)  throws IOException {
    	
    	String message="No input file";
    	if(file!=null){
    		
    		
    		 	FileChannel sourceChannel = null;
    		    FileChannel destChannel = null;
    		    try {
    		    	String mimeType = MimeTypes.getContentType(file.getName());
    		        sourceChannel = new FileInputStream(file).getChannel();
    		        destChannel = new FileOutputStream(Play.applicationPath.toString() + "/uploads/" + file.getName()).getChannel();
    		        destChannel.transferFrom(sourceChannel, 0, sourceChannel.size());
    		        message="ok";
    		    }catch (IOException e){
    		    	e.printStackTrace();
    		    }
    		    finally{
    		           sourceChannel.close();
    		           destChannel.close();
    		       }
    		    
    	}
    	
    	if(param1 != null) {
    		message += " ;param1 = " + param1;
    	}
    	renderText(message);
    	
    }

}