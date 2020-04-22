package com.demo.controllers;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;

import javax.annotation.PostConstruct;

import com.demo.dao.TeamDao;
import com.demo.model.Player;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.demo.model.Team;

@Controller
public class WhateverConroller {

	@Autowired
	private TeamDao teamDao;


	@PostConstruct
	public void init(){
		Team team = new Team();
		team.setId(1L);
		team.setName("Karpaty");
		team.setLocation("Lviv");
		team.setMascotte("Lion");
		team.setPlayers(new HashSet<>(Arrays.asList(new Player("Khudobiak", "attacker"))));
		teamDao.save(team);
	}
	
	@RequestMapping("/hi/{name}")
	public String hello(Map model, @PathVariable String name){
		model.put("name", name);
		return "hello";
	}
	
	@RequestMapping("/teams/{name}")
	public @ResponseBody Team team(@PathVariable String name){
		return teamDao.findByName(name);
	}
}