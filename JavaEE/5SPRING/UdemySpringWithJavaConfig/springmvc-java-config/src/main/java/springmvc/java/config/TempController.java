package springmvc.java.config;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@Controller
@RequestMapping("/temp")
public class TempController {

    @RequestMapping(method = RequestMethod.GET)
    public String index(Model model){
        model.addAttribute("someval", "9999");
        return "index";
    }
}
