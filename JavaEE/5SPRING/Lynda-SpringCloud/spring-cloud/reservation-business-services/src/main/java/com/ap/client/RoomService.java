package com.ap.client;

import com.ap.domain.Room;
import org.springframework.cloud.netflix.feign.FeignClient;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(value = "LYNDA-SPRING-CLOUD-ROOM-SERVICES")
public interface RoomService {
    @RequestMapping(value = "/rooms", method = RequestMethod.GET)
    List<Room> findAll(@RequestParam(name = "roomNumber", required = false)String  roomNumber);
}
