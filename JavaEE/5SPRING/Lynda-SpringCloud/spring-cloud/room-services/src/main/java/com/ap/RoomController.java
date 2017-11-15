package com.ap;

import java.util.Collections;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.annotations.ApiOperation;

@RestController
@RequestMapping("/rooms")
public class RoomController {
	@Autowired
	RoomRepository repository;
	
	@RequestMapping(method = RequestMethod.GET)
	@ApiOperation(value = "Get all rooms", notes = "Get all rooms in the system", nickname = "getRooms")
	public Iterable<Room> findAll(@RequestParam(name="roomNumber", required = false) String roomNumber){
		if(roomNumber != null) {
			return Collections.singletonList(repository.findByRoomNumber(roomNumber));
		}
		Iterable<Room> findAll = repository.findAll();
		return  findAll;
	}
}
