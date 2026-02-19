package com.ap.controller;

import com.ap.dto.ExecuteRequest;
import com.ap.dto.ExecuteResponse;
import com.ap.service.PythonExecutorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class PythonExecutionController {

    @Autowired
    private PythonExecutorService executorService;

    @PostMapping("/execute-python")
    public ResponseEntity<ExecuteResponse> executePython(@RequestBody ExecuteRequest request) {
        ExecuteResponse response = executorService.execute(request.getClassName(), request.getCode());
        return ResponseEntity.ok(response);
    }
}
