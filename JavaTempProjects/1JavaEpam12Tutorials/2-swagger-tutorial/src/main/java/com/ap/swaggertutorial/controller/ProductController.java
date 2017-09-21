package com.ap.swaggertutorial.controller;

import com.ap.swaggertutorial.model.Product;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;

@RestController
@RequestMapping("/product")
@Api(value = "onlinestore", description = "Operations with available products")
public class ProductController {


    @ApiOperation(value = "View a list of available products", response = Iterable.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successfully retrieved list"),
            @ApiResponse(code = 401, message = "You are not authorized to view the resource"),
            @ApiResponse(code = 403, message = "Resource is forbidden")
    })
    @RequestMapping(value = "/list", method= RequestMethod.GET, produces = "application/json")
    public Iterable<Product> list(Model model){
        ArrayList<Product> result = new ArrayList<>();
        result.add(null);
        return result;
    }

    @ApiOperation(value = "Delete a product")
    @RequestMapping(value="/delete/{id}", method = RequestMethod.GET, produces = "application/json")
    public ResponseEntity delete(@PathVariable Integer id){
        return new ResponseEntity("Product deleted successfully", HttpStatus.OK);
    }

    @ApiOperation(value = "Add a product")
    @RequestMapping(value = "/add", method = RequestMethod.POST, produces = "application/json")
    public ResponseEntity saveProduct(@RequestBody String [] args){
        return new ResponseEntity("Product saved successfully", HttpStatus.OK);
    }

}