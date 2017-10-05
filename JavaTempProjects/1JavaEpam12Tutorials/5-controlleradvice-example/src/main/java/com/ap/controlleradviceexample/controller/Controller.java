package com.ap.controlleradviceexample.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

/**
 * Created by andrii on 24.09.17.
 */
@RestController
@RequestMapping("/")
public class Controller {
    @RequestMapping("/")
    public String index() throws IOException {
        throw new IOException("E");
    }
}
