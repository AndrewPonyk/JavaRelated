package controllers;

import play.*;
import play.libs.F.Promise;
import play.mvc.*;

import java.util.*;

import jobs.GeneratePDFJob;

import models.*;

public class Application extends Controller {

	
	public static void generatePDF(){
		
	 
		long lStartTime = System.currentTimeMillis();


	 
		//Usual solution
/*		int result=0;
		//long process
		for(int i=0;i<3000004;i++){
			int k=i;
			k=k*2;
			result=k++;
		}*/

		
		
		//Solution with request suspending
		Promise<Integer> pdf=new GeneratePDFJob().now();
		Integer resultPdf=await(pdf);
		
	
		
		long lEndTime = System.currentTimeMillis();		
		long difference = lEndTime - lStartTime;
		System.out.println("Total execution time :"+difference);
		
		//renderText("generated PDF :..." + result);
		renderText("result id "+resultPdf);
	}
	
	
    public static void index() {
        render();
    }

}