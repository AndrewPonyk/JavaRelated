package com.hellospringdemo;

import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Created by andrii on 09.04.17.
 */
@Controller
public class HomeController {
    @RequestMapping("/home")
    public String index(){
        return "Home";
    }

}
