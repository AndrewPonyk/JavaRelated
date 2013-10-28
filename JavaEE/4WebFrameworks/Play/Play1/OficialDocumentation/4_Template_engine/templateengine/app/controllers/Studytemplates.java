package controllers;

import groovy.lang.Closure;

import java.io.PrintWriter;
import java.util.Map;

import play.mvc.Controller;
import play.templates.GroovyTemplate.ExecutableTemplate;
import play.templates.JavaExtensions;

public class Studytemplates extends Controller {

	public static void index(){
		
		render();
	}

}
