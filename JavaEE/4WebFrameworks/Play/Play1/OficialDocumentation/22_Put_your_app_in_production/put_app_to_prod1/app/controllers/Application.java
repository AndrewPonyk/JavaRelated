package controllers;

import play.*;
import play.mvc.*;

import java.util.*;

import models.*;

public class Application extends Controller {

    public static void index() {
    	int result=1;
    	for(int i=0;i<10;i++){
    		result*=2;
    	}
        render(result);
    }

}