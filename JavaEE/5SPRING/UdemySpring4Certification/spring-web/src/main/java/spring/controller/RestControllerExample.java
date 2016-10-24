package spring.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

@RestController  //will add automatically the @ResponseBody annotation to all methods // replacement for @Controller and @ResponseBody
public class RestControllerExample {
	
	  @Autowired
	  private RestTemplate restTemplate;	 
	  
	  //JSON echo service call site: http://www.jsontest.com/
	  @RequestMapping(value = "/echo/person1/{person1}/person2/{person2}", method = RequestMethod.GET)
	  public String echo(
			  @PathVariable(value="person1") String person1,
			  @PathVariable(value="person2") String person2) {

		  //JSON http://echo.jsontest.com/person1/tunatore/person2/testuser
		  ResponseEntity<String> response = restTemplate.getForEntity(
			        "http://echo.jsontest.com/person1/{person1}/person2/{person2}",
			        String.class,person1,person2);

		  System.out.println("RestControllerExample echoPersons return response: " + response);
		  return response.toString();
	 }
	  
	  @RequestMapping(value = "/responseStatusCode", method = RequestMethod.GET)	 
	  public String responseStatusTest() throws Exception {
		  
		  //responseStatusTest
		  System.out.println("RestControllerExample responseStatusTest");
		  throw new Exception("responseStatusTest exception");		
	 }
	 
	  @ExceptionHandler
	  @ResponseStatus(value = HttpStatus.BAD_GATEWAY,reason="PROVIDE YOUR REASON HERE")
	  public void handleException(Exception ex) {
		  System.out.println(ex);
	  }
	  	 

}
