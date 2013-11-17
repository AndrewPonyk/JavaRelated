package controllers;

import play.*;
import play.mvc.*;

import java.util.*;

import org.apache.log4j.DailyRollingFileAppender;

import org.apache.log4j.RollingFileAppender;

import models.*;

public class Application extends Controller {

    public static void index() {
    	
    	org.apache.log4j.Logger.getLogger("Rolling").info("some");
    	
    	
    	org.apache.log4j.DailyRollingFileAppender dd=new DailyRollingFileAppender();
    	
    	
    	org.apache.log4j.Logger.getLogger("Rolling").debug("debug");
    	
    	
    	Logger.error("1111");
    	Logger.info("222");
        render();
    }

}