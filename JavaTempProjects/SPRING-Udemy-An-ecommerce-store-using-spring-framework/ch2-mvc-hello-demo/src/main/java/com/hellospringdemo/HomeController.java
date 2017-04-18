package com.hellospringdemo;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Arrays;
import java.util.List;

/**
 * Created by andrii on 09.04.17.
 */
@Controller
public class HomeController {

    @RequestMapping("/home")
    public String index(Model model){
        model.addAttribute("greetings", "12345");
        List<String> listData = Arrays.asList(new String[]{"a", "fd", "abc"});
        model.addAttribute("listData", listData);
        return "Home";
    }

    @RequestMapping("/home1")
    public String index1(Model model){
        model.addAttribute("greetings", "12345");
        List<String> listData = Arrays.asList(new String[]{"a", "fd", "abc"});
        model.addAttribute("listData", listData);
        return "Home";
    }

}
