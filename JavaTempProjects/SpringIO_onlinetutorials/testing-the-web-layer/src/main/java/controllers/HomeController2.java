package controllers;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import services.GreetingService;

@Controller
public class HomeController2 {
    @Autowired
    private GreetingService greetingService;

    @RequestMapping("/greeting")
    public @ResponseBody String greeting(){
        return greetingService.greet();
    }

}
