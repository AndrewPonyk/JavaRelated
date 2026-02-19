package com.ap.controller;

import com.ap.dto.ExecuteRequest;
import com.ap.dto.ExecuteResponse;
import com.ap.service.JavaExecutorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class CodeExecutionController {

    @Autowired
    private JavaExecutorService executorService;

    @PostMapping("/execute")
    public ResponseEntity<ExecuteResponse> executeCode(@RequestBody ExecuteRequest request) {
        ExecuteResponse response = executorService.execute(request.getClassName(), request.getCode());
        return ResponseEntity.ok(response);
    }
}
