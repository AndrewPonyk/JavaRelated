package controllers;

import play.*;
import play.data.binding.As;
import play.libs.MimeTypes;
import play.mvc.*;
import play.mvc.Http.Header;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.*;

import com.thoughtworks.xstream.io.path.Path;

import models.*;

public class Application extends Controller {

    public static void index() {
        render();
    }
    
    public static void showParams(Long id){
    	// if we pass Long id to the action arguments Play will try cast value to Long ,
    	// if value is incorrect there will be id==null
    	if(id==null) System.out.println("id == null"); 
    	
    	String paramsId=params.get("id"); // get id from params
    	renderArgs.put("rederArgsId", paramsId);
    	
    	render(id);  // so on the page we have two ways to get id 
    	// using ${id}  or ${renderArgsId}
    	//render("Application/index.html",null); // if we dont want to create show.html template
    }
 
    public static void getDate(@As("dd-MM-yyyy")GregorianCalendar originalDate,Long addDays){
    	
    	originalDate.add(Calendar.DAY_OF_WEEK, addDays.intValue());
    	
    	Date result=originalDate.getTime();
    	String resultDate=new SimpleDateFormat("dd/MM/yyyy").format(result);
    	
    	
    	render(resultDate,addDays);
    }
    
    
    public static void saveFile(File file) throws IOException{
    	String message="something wrong";
    	if(file!=null){
    		
    		
    		 	FileChannel sourceChannel = null;
    		    FileChannel destChannel = null;
    		    try {
    		    	String mimeType = MimeTypes.getContentType(file.getName());
    		        sourceChannel = new FileInputStream(file).getChannel();
    		        destChannel = new FileOutputStream("/home/andrew/temp.jpg").getChannel();
    		        destChannel.transferFrom(sourceChannel, 0, sourceChannel.size());
    		        message="ok";
    		    }finally{
    		           sourceChannel.close();
    		           destChannel.close();
    		       }
    	}
    	render(message);
    }
    
    public static void actionWithoutView(){
    	 renderXml("<unreadmessages>"+100+"</unreadmessages>");

    	//renderText("Hello");
    	
    }
    
    public static void downloadFile(Long id){
    		   response.setContentTypeIfNotSet("pdf");
    		   
    		   java.io.InputStream binaryData;
			try {
				binaryData = new FileInputStream("/media/88D4CBF1D4CBDF94/pro_html5_and_css3_design_patterns.pdf");
				renderBinary(binaryData);
			} catch (FileNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				renderText("Error");
				}	   
    }
    
    public static void anotherIndex(){
    	index();
    }

}