package com.demo.controllers;

import java.util.Map;

import javax.annotation.PostConstruct;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import model.Team;

@Controller
public class WhateverConroller {
	
	private Team team;
	
	@PostConstruct
	public void init(){
		team = new Team();
		team.setName("Karpaty");
		team.setLocation("Lviv");
		team.setMascotte("Lion");
	}
	
	@RequestMapping("/hi/{name}")
	public String hello(Map model, @PathVariable String name){
		model.put("name", name);
		return "hello";
	}
	
	@RequestMapping("/team")
	public @ResponseBody Team team(){
		return team;
	}
}