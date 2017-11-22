package com.ap;

import com.ap.client.RoomService;
import com.ap.domain.Room;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@RestController
public class RoomReservationController {

    @Autowired
    private RoomService roomService;

    @RequestMapping(value = "/rooms", method = RequestMethod.GET)
    public List<Room> getAllRooms(){
        return roomService.findAll(null);
    }


    // load balanced RestTemplate
//    @Autowired
//    private RestTemplate restTemplate;
//
//    @RequestMapping(value = "/rooms", method = RequestMethod.GET)
//    public List<Room> getAllRooms(){
//        //LYNDA-SPRING-CLOUD-ROOM-SERVICES - name of app in eureka
//        ResponseEntity<List<Room>> roomsResponse = this.restTemplate.exchange(
//                "http://LYNDA-SPRING-CLOUD-ROOM-SERVICES/rooms",
//                HttpMethod.GET,
//                HttpEntity.EMPTY,
//                new ParameterizedTypeReference<List<Room>>() {
//                }
//        );
//        return roomsResponse.getBody();
//    }
}
