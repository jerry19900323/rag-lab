package com.example.raglab.api;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Converts expected input failures into consistent client-facing responses. */
@RestControllerAdvice
public class ApiExceptionHandler {

    /** Reports document validation failures as HTTP 400 responses. */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleInvalidInput(IllegalArgumentException exception) {
        return Map.of("error", exception.getMessage());
    }
}
