package com.my;

import com.github.tomakehurst.wiremock.common.FileSource;
import com.github.tomakehurst.wiremock.extension.ResponseTransformer;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.Response;
import com.github.tomakehurst.wiremock.http.ResponseDefinition;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class LibraryBookReturnDateTransformer extends ResponseTransformer {

//    @Override
//    public Response transform(Request request, Response response, FileSource fileSource, Parameters parameters) {
//
//        int daysInTodaysMonth = LocalDate.now().lengthOfMonth();
//        LocalDate bookReturnDate =LocalDate.now().plusDays(daysInTodaysMonth);
//
//        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("ddMMyyyy");
//
//        String formattedReturnDate=bookReturnDate.format(formatter);
//
//        return Response.Builder.like(response)
//                .but().body("<Book><bookID>1234</bookID><name>TheGreatGatsby</name><bookReturnDate>"+formattedReturnDate+"</bookReturnDate>")
//                .build();
//    }

    @Override
    public ResponseDefinition transform(Request request, ResponseDefinition responseDefinition, FileSource fileSource) {
        responseDefinition.setBody("transformeddd!!!!");
        return responseDefinition;
    }

    @Override
    public boolean applyGlobally() {
        return false;
    }

    @Override
    public String name() {
        return "librarytransformer";
    }
}