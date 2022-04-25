package com.ap.demoheroku;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/pingcontroller")
public class PingController {

    @GetMapping("ping")
    public String ping(){
        return "ping";
    }
}
