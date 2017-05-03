package com.javacodegeeks.resteasy.ws;

import com.javacodegeeks.resteasy.model.Employee;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by aponyk on 03.05.2017.
 */
//http://www.vinaysahni.com/best-practices-for-a-pragmatic-restful-api
/*
* Once you have your resources defined, you need to identify what actions apply to them and how those would map to your API. RESTful principles provide strategies to handle CRUD actions using HTTP methods mapped as follows:

GET /tickets - Retrieves a list of tickets
GET /tickets/12 - Retrieves a specific ticket
POST /tickets - Creates a new ticket
PUT /tickets/12 - Updates ticket #12
PATCH /tickets/12 - Partially updates ticket #12
DELETE /tickets/12 - Deletes ticket #12
* */

@Path("/sampleservice")
public class SampleService {

    private static Map<String, Employee> employees = new HashMap<String, Employee>();

    static {

        Employee employee1 = new Employee();
        employee1.setEmployeeId("1");
        employee1.setEmployeeName("Fabrizio");
        employee1.setJob("Software Engineer");
        employees.put(employee1.getEmployeeId(), employee1);

        Employee employee2 = new Employee();
        employee2.setEmployeeId("2");
        employee2.setEmployeeName("Justin");
        employee2.setJob("Business Analyst");
        employees.put(employee2.getEmployeeId(), employee2);

    }

    @GET
    @Path("/hello")
    @Produces("text/plain")
    public String hello(){
        return "Hello World";
    }

    @GET
    @Path("/echo/{message}")
    @Produces("text/plain")
    public String echo(@PathParam("message")String message){
        return message;
    }

    @GET
    @Path("/employees")
    @Produces("application/xml")
    public List<Employee> listEmployees(){
        return new ArrayList<Employee>(employees.values());
    }

    @GET
    @Path("/employees/{employeeid}")
    @Produces("application/xml")
    public Employee getEmployee(@PathParam("employeeid")String employeeId){
        return employees.get(employeeId);
    }

    @GET
    @Path("/json/employees/")
    @Produces("application/json")
    public List<Employee> listEmployeesJSON(){
        return new ArrayList<Employee>(employees.values());
    }

    @GET
    @Path("/json/employees/{employeeid}")
    @Produces("application/json")
    public Employee getEmployeeJSON(@PathParam("employeeid")String employeeId){
        return employees.get(employeeId);
    }

}