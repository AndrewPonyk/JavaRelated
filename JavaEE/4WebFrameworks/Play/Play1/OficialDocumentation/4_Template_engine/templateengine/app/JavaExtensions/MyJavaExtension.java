package JavaExtensions;

import groovy.lang.Closure;

import java.io.PrintWriter;
import java.util.Map;

import play.Play;
import play.templates.JavaExtensions;
import play.templates.GroovyTemplate.ExecutableTemplate;

public class MyJavaExtension  extends  JavaExtensions{
	
	public static String ccyAccount(Number number, String currencyName){
		
		return "Sum: " + number+"  "+currencyName;
		
	}

}
