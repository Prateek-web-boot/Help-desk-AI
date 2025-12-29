package com.substring.helpdesk.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.context.request.WebRequest;
import java.util.Map;

@Controller
public class GlobalExceptionHandler {

    public ResponseEntity<Object> handleAllExceptions(Exception ex, WebRequest request) {
        return new ResponseEntity<>(
                Map.of("message", ex.getMessage(), "status", "error"),
                HttpStatus.INTERNAL_SERVER_ERROR
        );
    }
}
