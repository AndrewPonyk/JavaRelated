package controllers;

import play.*;
import play.libs.F.Promise;
import play.libs.WS;
import play.mvc.*;

import java.util.*;
import java.util.concurrent.ExecutionException;

import com.sun.xml.internal.ws.addressing.WsaActionUtil;

import jobs.GeneratePDFJob;
import models.*;

public class Application extends Controller {

	
	public static void generatePDF(){
	 
		long lStartTime = System.currentTimeMillis();
	 
		//Usual solution
		int result=0;
		//long process
		for(int i=0;i<9000004;i++){
			int k=i;
			k=k*2;
			result=k++;
		}

		long lEndTime = System.currentTimeMillis();		
		long difference = lEndTime - lStartTime;
		System.out.println("Total execution time :"+difference);
		
		renderText("generated PDF :..." + result);
	}
	
	// faaaaaaster 
	public static void generatePDFWithRequestSuspending(){
		
		long lStartTime = System.currentTimeMillis();
		
		//Solution with request suspending
				Promise<Integer> pdf=new GeneratePDFJob().now();
				Integer resultPdf=await(pdf);			
				
				long lEndTime = System.currentTimeMillis();		
				long difference = lEndTime - lStartTime;
				System.out.println("Total execution time :"+difference);
				renderText("result id "+resultPdf);
				
	}
	
	
	public static void moreThanOneJobsAsync() throws InterruptedException, ExecutionException{
		
		//usual solution
/*		String googleSource = WS.url("http://google.com").get().getString();
		String amazonSource = WS.url("http://amazon.com").get().getString();
		String wikiSource= WS.url("http://wikipedia.com").get().getString();*/
		
		Promise<WS.HttpResponse>	google=WS.url("http://google.com").getAsync();
		Promise<WS.HttpResponse>	amazon=WS.url("http://amazon.com").getAsync();
		Promise<WS.HttpResponse>	  wiki=WS.url("http://wikipedia.com").getAsync();
		
		
		Promise<List<WS.HttpResponse>> promises=Promise.waitAll(google,amazon,wiki);
		String googleSource=promises.get().get(0).getString();
		String amazonSource=promises.get().get(1).getString();;
		String wikiSource=promises.get().get(0).getString();
		
		renderText("OK :" +"\n"+googleSource+"\n"+amazonSource+"\n"+wikiSource);
	}
	
	public static void callWebSocet(){
		WS.url("http://localhost:9000/helloSocket");
		
		
		renderText("Calling WebSocket");
		
	}
	
	
    public static void index() {
        render();
    }

}