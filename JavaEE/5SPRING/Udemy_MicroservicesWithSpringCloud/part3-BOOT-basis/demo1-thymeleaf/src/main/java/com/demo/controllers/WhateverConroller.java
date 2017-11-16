package com.demo.controllers;

import java.util.Map;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Controller
public class WhateverConroller {
	
	@RequestMapping("/hi/{name}")
	public String hello(Map model, @PathVariable String name){
		model.put("name", name);
		return "hello";
	}
}
