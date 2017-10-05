package com.ap.controlleradviceexample.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;

import java.io.IOException;

@ControllerAdvice
public class ControllerAdvice1 {
    @ExceptionHandler(IOException.class)
    public ResponseEntity<String> handleIOException(Exception ex, WebRequest request){
        return new ResponseEntity<String>("error happens", new HttpHeaders(), HttpStatus.CONFLICT);
    }
}
