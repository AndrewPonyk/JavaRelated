package webservices;

import javax.jws.WebService;

@WebService
public class HelloWorld {

    public void constructon(){

    }
    public String hello(String name){
        return "Hello, " + name;
    }
}
